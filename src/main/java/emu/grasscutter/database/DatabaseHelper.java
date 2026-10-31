package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.mongodb.MongoWriteException;

import dev.morphia.Datastore;
import dev.morphia.query.*;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.*;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.AccountUsernamePolicy;
import emu.grasscutter.game.BannedIp;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.activity.PlayerActivityData;
import emu.grasscutter.game.activity.musicgame.MusicGameBeatmap;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.battlepass.BattlePassManager;
import emu.grasscutter.game.friends.Friendship;
import emu.grasscutter.game.gacha.GachaRecord;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.mail.Mail;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.utils.objects.Returnable;
import emu.grasscutter.server.threading.ManagedThreadPoolExecutor;
import emu.grasscutter.server.threading.ThreadPoolConfig;
import emu.grasscutter.server.threading.ThreadPoolConfigResolver;
import emu.grasscutter.server.threading.ThreadPoolType;
import io.netty.util.concurrent.FastThreadLocalThread;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import org.bson.Document;

public final class DatabaseHelper {
    private static final AccountCreationService.Store ACCOUNT_CREATION_STORE =
            new AccountCreationService.Store() {
                @Override
                public int nextId() {
                    return DatabaseManager.getNextId(Account.class);
                }

                @Override
                public AccountCreationService.InsertResult tryInsert(Account account) {
                    try {
                        runSynchronousDatabaseWrite(
                                () -> DatabaseManager.getAccountDatastore().insert(account));
                        return AccountCreationService.InsertResult.INSERTED;
                    } catch (MongoWriteException e) {
                        if (e.getError() == null || e.getError().getCode() != 11000) throw e;
                        if (DatabaseHelper.getAccountByName(account.getUsername()) != null) {
                            return AccountCreationService.InsertResult.USERNAME_TAKEN;
                        }
                        if (DatabaseHelper.getAccountById(account.getId()) != null) {
                            return AccountCreationService.InsertResult.ID_TAKEN;
                        }
                        throw e;
                    }
                }
            };

    public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    /** Long enough that a pool which idles between logins does not churn its threads. */
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 600;

    /**
     * Queue bounds, sized per pool against how much traffic each one actually carries.
     *
     * <p>They are bounds rather than the unbounded queue this used to use. An unbounded queue never
     * rejects, so a database that cannot keep up simply grows the queue until the heap is gone,
     * with nothing in the logs until it is too late. A bound turns that into backpressure the
     * server can see and report.
     */
    public static final int DEFAULT_QUEUE_CAPACITY =
            Math.min(Math.max(AVAILABLE_PROCESSORS * 512, 1024), 8192);

    public static final int ACCOUNT_QUEUE_CAPACITY =
            Math.min(Math.max(AVAILABLE_PROCESSORS * 64, 256), 2048);

    public static final int ITEM_QUEUE_CAPACITY =
            Math.min(Math.max(AVAILABLE_PROCESSORS * 1024, 2048), 16384);

    public static final int GROUP_QUEUE_CAPACITY =
            Math.min(Math.max(AVAILABLE_PROCESSORS * 512, 1024), 8192);

    /**
     * Group instances with a save already queued, so a second save for the same instance is folded
     * into the pending one.
     *
     * <p>Scene scripts call cacheGadgetState and setCached constantly, and without this the same
     * instance is queued hundreds of times a second for writes that all produce the same document.
     * That alone keeps the group pool saturated on an otherwise idle server.
     *
     * <p>Membership is by object identity and is cleared before the write runs, not after, so a
     * change made while the write is in flight queues a fresh save rather than being dropped.
     */
    private static final Set<SceneGroupInstance> pendingGroupSaves =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Objects with a save already queued on the default pool.
     *
     * <p>A save writes the object as it is when the write runs, so a second one queued behind the
     * first has nothing left to write. Without this, work that touches an object repeatedly queues
     * a write every time: {@code /give all} alone put over a thousand avatar saves on a queue that
     * holds 1024, and the server then turned players away as overloaded for minutes afterwards.
     */
    private static final Set<Object> pendingDefaultSaves =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private static int coreThreads(int divisor) {
        return Math.max(1, AVAILABLE_PROCESSORS / divisor);
    }

    private static int maxThreads(int divisor) {
        return Math.max(coreThreads(divisor), AVAILABLE_PROCESSORS / divisor + 1);
    }

    /** Netty thread-locals only work on its own thread type, which is why these are not plain threads. */
    private static ThreadFactory databaseThreadFactory(String name) {
        var counter = new AtomicInteger();
        return runnable -> {
            var thread = new FastThreadLocalThread(runnable, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static ThreadPoolConfig databasePoolConfig(
            String name, int coreThreads, int maxThreads, int queueCapacity) {
        return ThreadPoolConfigResolver.resolve(
                name,
                ThreadPoolType.DATABASE,
                coreThreads,
                maxThreads,
                queueCapacity,
                DEFAULT_KEEP_ALIVE_SECONDS);
    }

    private static LinkedBlockingDeque<Runnable> databaseQueue(
            ThreadPoolConfig config, int fallbackCapacity) {
        int capacity = config.queueCapacity() > 0 ? config.queueCapacity() : fallbackCapacity;
        return new LinkedBlockingDeque<>(capacity);
    }

    private static final ThreadPoolConfig DEFAULT_POOL_CONFIG =
            databasePoolConfig(
                    "DATABASE_DEFAULT",
                    coreThreads(3),
                    Math.max(coreThreads(3), AVAILABLE_PROCESSORS),
                    DEFAULT_QUEUE_CAPACITY);
    private static final ThreadPoolConfig ACCOUNT_POOL_CONFIG =
            databasePoolConfig(
                    "DATABASE_ACCOUNT",
                    coreThreads(4),
                    Math.max(coreThreads(4), AVAILABLE_PROCESSORS / 2 + 1),
                    ACCOUNT_QUEUE_CAPACITY);
    private static final ThreadPoolConfig ITEM_POOL_CONFIG =
            databasePoolConfig("DATABASE_ITEM", coreThreads(3), maxThreads(2), ITEM_QUEUE_CAPACITY);
    private static final ThreadPoolConfig GROUP_POOL_CONFIG =
            databasePoolConfig("DATABASE_GROUP", coreThreads(3), maxThreads(2), GROUP_QUEUE_CAPACITY);

    /*
     * Four pools rather than one, so the traffic classes cannot starve each other: a burst of item
     * writes used to sit in front of the account save that a login was waiting on.
     *
     * All four use AbortPolicy internally so shutdown rejection is observable. DatabaseWriterManager
     * restores caller-runs backpressure for queue saturation while admission is still open.
     */
    @Getter
    private static final ExecutorService eventExecutor =
            new ManagedThreadPoolExecutor(
                    DEFAULT_POOL_CONFIG,
                    databaseQueue(DEFAULT_POOL_CONFIG, DEFAULT_QUEUE_CAPACITY),
                    databaseThreadFactory("database-default"),
                    new ThreadPoolExecutor.AbortPolicy());

    /** Low volume, but a login blocks on it. */
    @Getter
    private static final ExecutorService eventExecutorAccount =
            new ManagedThreadPoolExecutor(
                    ACCOUNT_POOL_CONFIG,
                    databaseQueue(ACCOUNT_POOL_CONFIG, ACCOUNT_QUEUE_CAPACITY),
                    databaseThreadFactory("database-account"),
                    new ThreadPoolExecutor.AbortPolicy());

    /** The highest-volume traffic on the server. */
    @Getter
    private static final ExecutorService eventExecutorItem =
            new ManagedThreadPoolExecutor(
                    ITEM_POOL_CONFIG,
                    databaseQueue(ITEM_POOL_CONFIG, ITEM_QUEUE_CAPACITY),
                    databaseThreadFactory("database-item"),
                    new ThreadPoolExecutor.AbortPolicy());

    /** Driven by scene scripts, which is why the dedup above matters. */
    @Getter
    private static final ExecutorService eventExecutorGroup =
            new ManagedThreadPoolExecutor(
                    GROUP_POOL_CONFIG,
                    databaseQueue(GROUP_POOL_CONFIG, GROUP_QUEUE_CAPACITY),
                    databaseThreadFactory("database-group"),
                    new ThreadPoolExecutor.AbortPolicy());

    private static final DatabaseWriterManager databaseWriters =
            new DatabaseWriterManager(
                    Map.of(
                            "default", eventExecutor,
                            "account", eventExecutorAccount,
                            "item", eventExecutorItem,
                            "group", eventExecutorGroup));

    private static final ItemPersistenceService itemWrites =
            new ItemPersistenceService(
                    databaseWriters,
                    eventExecutorItem,
                    DatabaseHelper::saveWithRetry,
                    item -> DatabaseManager.getGameDatastore().delete(item));

    static DatabaseWriterManager getWriterManager() {
        return databaseWriters;
    }

    /** Runs a direct write through the same admission gate as asynchronous persistence. */
    public static void runSynchronousDatabaseWrite(Runnable task) {
        databaseWriters.runSynchronous(task);
    }

    /** Returns a direct write's result while honoring shutdown and administrative barriers. */
    public static <T> T callSynchronousDatabaseWrite(Supplier<T> task) {
        return databaseWriters.callSynchronous(task);
    }

    private static void submitDatabaseWrite(ExecutorService executor, Runnable task) {
        databaseWriters.submit(executor, task);
    }

    public static DatabaseWriterManager.ShutdownResult shutdownWriters(Duration timeout) {
        return databaseWriters.shutdown(timeout);
    }

    /**
     * Whether a pool is backed up far enough that the server should stop letting players in.
     *
     * <p>The threshold is below the queue bound on purpose: by the time a queue is actually full
     * every submitting thread is running writes inline, and a login admitted at that point makes
     * the stall worse.
     */
    public static boolean isThreadPoolOverloaded(ThreadPoolExecutor executor, int maxCount) {
        return executor.getQueue().size() > maxCount * 0.7f;
    }

    /** The reason text written on an account auto-banned by an IP ban. */
    public static final String IP_BAN_REASON_PREFIX = "Banned IP: ";

    /**
     * The prefix used before the ban reason was in English.
     *
     * <p>Accounts banned back then carry it and have no bannedByIp field, so unbanning an IP still
     * matches on it to find them. Nothing writes it any more.
     */
    private static final String LEGACY_IP_BAN_REASON_PREFIX = "IP\u5df2\u5c01\u7981: ";

    public static void saveBannedIp(BannedIp bannedIp) {
        submitDatabaseWrite(
                eventExecutorAccount, () -> DatabaseManager.getAccountDatastore().save(bannedIp));
    }

    public static BannedIp getBannedIp(String ip) {
        if (ip == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(BannedIp.class)
                .filter(Filters.eq("_id", ip))
                .first();
    }

    public static boolean isIpBanned(String ip) {
        var ban = DatabaseHelper.getBannedIp(ip);
        return ban != null && ban.isActiveAt(System.currentTimeMillis() / 1000);
    }

    public static boolean removeBannedIp(String ip) {
        var banned = DatabaseHelper.getBannedIp(ip);
        if (banned == null) return false;
        runSynchronousDatabaseWrite(() -> DatabaseManager.getAccountDatastore().delete(banned));
        return true;
    }

    /**
     * Every account that was banned because of this IP.
     *
     * <p>Matched on the bannedByIp field, with the old reason-text pattern as a fallback so
     * accounts banned before that field existed are still found.
     */
    public static List<Account> getAccountsBannedByIp(String ip) {
        if (ip == null) return List.of();
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(
                        Filters.or(
                                Filters.eq("bannedByIp", ip),
                                Filters.regex("banReason")
                                        .pattern(
                                                "^"
                                                        + java.util.regex.Pattern.quote(
                                                                LEGACY_IP_BAN_REASON_PREFIX + ip))))
                .iterator()
                .toList();
    }

    /** Lifts the ban on every account this IP took down. Returns how many were unbanned. */
    public static int unbanAccountsBannedByIp(String ip) {
        int unbanned = 0;
        for (var account : DatabaseHelper.getAccountsBannedByIp(ip)) {
            try {
                unbanAccount(account);
                unbanned++;
            } catch (RuntimeException e) {
                Grasscutter.getLogger().error("Failed to unban account {}.", account.getId(), e);
            }
        }
        return unbanned;
    }

    /** The account fields a ban sets. */
    public static final String[] BAN_FIELDS = {
        "banReason", "banEndTime", "banStartTime", "isBanned", "bannedByIp"
    };

    public static void unbanAccount(Account account) {
        updateAccount(
                account,
                fresh -> {
                    fresh.setBanned(false);
                    fresh.setBanReason(null);
                    fresh.setBanEndTime(0);
                    fresh.setBanStartTime(0);
                    fresh.setBannedByIp(null);
                },
                BAN_FIELDS);
    }

    /**
     * Applies an administrative change to this account and to the online player's copy of it, then
     * writes only the changed fields. A whole-document save from either copy would put back
     * whatever else changed since it was loaded, such as a newer login token.
     */
    public static void updateAccount(Account account, Consumer<Account> change, String... fields) {
        change.accept(account);
        var server = Grasscutter.getGameServer();
        var online = server == null ? null : server.getPlayerByAccountId(account.getId());
        if (online != null && online.getAccount() != account) change.accept(online.getAccount());
        saveAccountFields(account, fields);
    }

    /**
     * Writes only these fields of the account, leaving the rest of the stored document alone.
     *
     * <p>The write is queued behind the account's earlier saves, so an older whole-document save can
     * never land afterwards and put the old fields back. A global write barrier would order it too,
     * but it rejects every other player's saves while it is held.
     */
    public static void saveAccountFields(Account account, String... fields) {
        var datastore = DatabaseManager.getAccountDatastore();
        var update = accountFieldUpdate(datastore, account, fields);
        databaseWriters.submitOrderedUncapped(
                eventExecutorAccount,
                new AccountKey(account.getId()),
                () -> datastore.getDatabase().getCollection("accounts").updateOne(eq("_id", account.getId()), update));
    }

    /** Orders one account's writes; whole-document saves and field updates share it. */
    private record AccountKey(String accountId) {}

    /** Kept for callers of the earlier synchronous form; {@link #saveAccountFields} is queued already. */
    public static void saveAccountFieldsAsync(Account account, String... fields) {
        saveAccountFields(account, fields);
    }

    /** A $set of the fields as Morphia would store them, and an $unset of those it would omit. */
    static Document accountFieldUpdate(Datastore datastore, Account account, String... fields) {
        var stored = datastore.getMapper().toDocument(account);
        var set = new Document();
        var unset = new Document();
        for (var field : fields) {
            if (stored.get(field) != null) set.put(field, stored.get(field));
            else unset.put(field, "");
        }
        var update = new Document();
        if (!set.isEmpty()) update.put("$set", set);
        if (!unset.isEmpty()) update.put("$unset", unset);
        return update;
    }

    /**
     * Saves an object on the account datastore.
     *
     * @param object The object to save.
     */
    public static void saveAccountAsync(Object object) {
        if (object instanceof Account account) {
            submitAccountSave(account, () -> DatabaseManager.getAccountDatastore().save(account));
            return;
        }
        submitDatabaseWrite(
                eventExecutorAccount, () -> DatabaseManager.getAccountDatastore().save(object));
    }

    private static void submitAccountSave(Account account, Runnable save) {
        if (account.getId() == null) {
            submitDatabaseWrite(eventExecutorAccount, save);
        } else {
            databaseWriters.submitOrderedUncapped(eventExecutorAccount, new AccountKey(account.getId()), save);
        }
    }

    /**
     * Saves an object on the game datastore, on the pool that matches what it is.
     *
     * @param object The object to save.
     */
    public static void saveGameAsync(Object object) {
        if (object == null) return;

        // The three types are unrelated, so the order of these tests carries no meaning.
        if (object instanceof GameItem gameItem) {
            itemWrites.save(gameItem);
        } else if (object instanceof SceneGroupInstance groupInstance) {
            submitGroupSave(groupInstance);
        } else if (object instanceof Account account) {
            submitAccountSave(account, () -> saveWithRetry(account));
        } else {
            submitDefaultSave(object);
        }
    }

    /**
     * Orders writes by object identity: a save queued while an earlier one runs must not land
     * first and leave the older state in the database.
     */
    private record SameObject(Object object) {
        @Override
        public boolean equals(Object other) {
            return other instanceof SameObject same && same.object == object;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(object);
        }
    }

    /** Queues a save on the default pool, unless one is already queued for this object. */
    private static void submitDefaultSave(Object object) {
        // Already queued: that pending write will pick up this change too.
        if (!pendingDefaultSaves.add(object)) return;

        try {
            databaseWriters.submitOrderedUncapped(
                    eventExecutor,
                    new SameObject(object),
                    () -> {
                        // Cleared before the write, not after: anything changed while this one runs
                        // has to be able to queue a write of its own.
                        pendingDefaultSaves.remove(object);
                        saveWithRetry(object);
                    });
        } catch (RuntimeException submitFailed) {
            // Clear the mark, or a rejected submit would leave this object unable to queue again.
            pendingDefaultSaves.remove(object);
            throw submitFailed;
        }
    }

    private static void submitGroupSave(SceneGroupInstance groupInstance) {
        // Already queued: that pending write will pick up this change too.
        if (!pendingGroupSaves.add(groupInstance)) return;

        try {
            databaseWriters.submitOrderedUncapped(
                    eventExecutorGroup,
                    new SameObject(groupInstance),
                    () -> {
                        pendingGroupSaves.remove(groupInstance);
                        saveWithRetry(groupInstance);
                    });
        } catch (RuntimeException submitFailed) {
            // Clear the mark, or a rejected submit would leave this instance unable to queue again.
            pendingGroupSaves.remove(groupInstance);
            throw submitFailed;
        }
    }

    /**
     * Saves on the executor, retrying the two failures that are races rather than real errors.
     *
     * <p>An unguarded save swallows both: a duplicate key means something else inserted the entity
     * first, and a ConcurrentModificationException means a player collection was being mutated on
     * another thread while Morphia walked it - typically during login. Both used to lose the write
     * silently, so progress simply disappeared.
     */
    private static void saveWithRetry(Object object) {
        var name = object.getClass().getSimpleName();
        try {
            DatabaseManager.getGameDatastore().save(object);
        } catch (MongoWriteException e) {
            if (e.getError() == null || e.getError().getCode() != 11000) {
                Grasscutter.getLogger().error("Failed to save {}.", name, e);
                return;
            }
            // The id is reflected back onto the object by the failed insert, so the retry
            // becomes a replace.
            try {
                DatabaseManager.getGameDatastore().save(object);
            } catch (Throwable t) {
                Grasscutter.getLogger().error("Failed to save {} after a duplicate key.", name, t);
            }
        } catch (ConcurrentModificationException e) {
            for (var attempt = 0; attempt < 8; attempt++) {
                try {
                    Thread.sleep(100);
                    DatabaseManager.getGameDatastore().save(object);
                    return;
                } catch (ConcurrentModificationException ignored) {
                    // The other thread has not settled yet.
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Throwable t) {
                    Grasscutter.getLogger().error("Failed to save {}.", name, t);
                    return;
                }
            }
            Grasscutter.getLogger().error("Failed to save {} - it kept being modified.", name, e);
        } catch (Throwable t) {
            Grasscutter.getLogger().error("Failed to save {}.", name, t);
        }
    }

    /**
     * Runs a runnable on the event executor. Should be limited to database-related operations.
     *
     * @param runnable The runnable to run.
     */
    public static void asyncOperation(Runnable runnable) {
        submitDatabaseWrite(eventExecutor, runnable);
    }

    /**
     * Fetches an object asynchronously.
     *
     * @param task The task to run.
     * @return The future.
     */
    public static <T> CompletableFuture<T> fetchAsync(Returnable<T> task) {
        var future = new CompletableFuture<T>();

        // Run the task on the event executor.
        submitDatabaseWrite(
                eventExecutor,
                () -> {
                    try {
                        future.complete(task.invoke());
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });

        return future;
    }

    public static Account createAccount(String username) {
        return createAccountWithUid(username, 0);
    }

    public static Account createAccountWithUid(String username, int reservedUid) {
        if (!AccountUsernamePolicy.isValid(username)) return null;
        // Unique names only
        if (DatabaseHelper.checkIfAccountExists(username)) {
            return null;
        }

        // Make sure there are no id collisions
        if (reservedUid > 0) {
            // Cannot make account with the same uid as the server console
            if (reservedUid == GameConstants.SERVER_CONSOLE_UID) {
                return null;
            }

            if (DatabaseHelper.checkIfAccountExists(reservedUid)) {
                return null;
            }

            // Make sure no existing player already has this id.
            if (DatabaseHelper.checkIfPlayerExists(reservedUid)) {
                return null;
            }
        }

        // Account creation is synchronous and insert-only. The unique indexes, not the pre-checks
        // above, are the authority when another process creates the same account concurrently.
        Account account = new Account();
        account.setUsername(username);

        if (reservedUid > 0) {
            account.setReservedPlayerUid(reservedUid);
        }

        return AccountCreationService.insert(account, ACCOUNT_CREATION_STORE);
    }

    /**
     * Creates an account with a hashed password and an email address.
     *
     * <p>Separate from the two-argument version, which stores whatever it is handed verbatim.
     * Account.verifyPassword accepts both a BCrypt hash and a legacy plaintext value, so a password
     * stored raw does work - it just stays raw forever, since the authenticator only upgrades an
     * account whose stored password is empty.
     *
     * @return the new account, or null if the username is taken.
     */
    public static Account createAccountWithHashedPassword(
            String username, String password, String email) {
        if (!AccountUsernamePolicy.isValid(username)) return null;
        if (DatabaseHelper.getAccountByName(username) != null) return null;

        var account = new Account();
        account.setUsername(username);
        account.setPassword(BCrypt.withDefaults().hashToString(12, password.toCharArray()));
        if (email != null && !email.isBlank()) account.setEmail(email);
        return AccountCreationService.insert(account, ACCOUNT_CREATION_STORE);
    }

    public static Account createAccountWithPassword(String username, String password) {
        if (!AccountUsernamePolicy.isValid(username)) return null;
        // Unique names only
        Account exists = DatabaseHelper.getAccountByName(username);
        if (exists != null) {
            return null;
        }

        // Account
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(password);
        return AccountCreationService.insert(account, ACCOUNT_CREATION_STORE);
    }

    public static void saveAccount(Account account) {
        DatabaseHelper.saveAccountAsync(account);
    }

    public static Account getAccountByName(String username) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("username", username))
                .first();
    }

    public static Account getAccountByToken(String token) {
        if (token == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("token", token))
                .first();
    }

    public static Account getAccountBySessionKey(String sessionKey) {
        if (sessionKey == null) return null;
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("sessionKey", sessionKey))
                .first();
    }

    public static Account getAccountById(String uid) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("_id", uid))
                .first();
    }

    public static Account getAccountByPlayerId(int playerId) {
        return DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(Filters.eq("reservedPlayerId", playerId))
                .first();
    }

    public static boolean checkIfAccountExists(String name) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("username", name))
                        .count()
                > 0;
    }

    public static boolean checkIfAccountExists(int reservedUid) {
        return DatabaseManager.getAccountDatastore()
                        .find(Account.class)
                        .filter(Filters.eq("reservedPlayerId", reservedUid))
                        .count()
                > 0;
    }

    public static void deleteAccount(Account target) {
        AccountDeletionService.delete(target);
    }

    public static <T> Stream<T> getByGameClass(Class<T> classType) {
        return DatabaseManager.getGameDatastore().find(classType).stream();
    }

    @Deprecated(forRemoval = true)
    public static List<Player> getAllPlayers() {
        return DatabaseManager.getGameDatastore().find(Player.class).stream().toList();
    }

    public static Player getPlayerByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("_id", id))
                .first();
    }

    @Deprecated
    public static Player getPlayerByAccount(Account account) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    public static Player getPlayerByAccount(Account account, Class<? extends Player> playerClass) {
        return DatabaseManager.getGameDatastore()
                .find(playerClass)
                .filter(Filters.eq("accountId", account.getId()))
                .first();
    }

    /**
     * Use {@link DatabaseHelper#getPlayerByAccount(Account, Class)} for creating a real player. This
     * method is used for fetching the player's data.
     *
     * @param accountId The account's ID.
     * @return The player.
     */
    public static Player getPlayerByAccount(String accountId) {
        return DatabaseManager.getGameDatastore()
                .find(Player.class)
                .filter(Filters.eq("accountId", accountId))
                .first();
    }

    public static boolean checkIfPlayerExists(int uid) {
        return DatabaseManager.getGameDatastore()
                        .find(Player.class)
                        .filter(Filters.eq("_id", uid))
                        .count()
                > 0;
    }

    public static void generatePlayerUid(Player character, int reservedId) {
        PlayerUidAllocator.assignReserved(character, reservedId);
    }

    public static int getNextPlayerId(int reservedId) {
        return PlayerUidAllocator.next(reservedId);
    }

    public static void savePlayer(Player character) {
        DatabaseHelper.saveGameAsync(character);
    }

    public static void saveAvatar(Avatar avatar) {
        DatabaseHelper.saveGameAsync(avatar);
    }

    /**
     * Fetches all avatars of a player.
     *
     * @param player The player.
     * @return The list of avatars.
     */
    public static List<Avatar> getAvatars(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Avatar.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveItem(GameItem item) {
        DatabaseHelper.saveGameAsync(item);
    }

    public static void deleteItem(GameItem item) {
        itemWrites.delete(item);
    }

    /**
     * Fetches all items of a player.
     *
     * @param player The player.
     * @return The list of items.
     */
    public static List<GameItem> getInventoryItems(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameItem.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static List<Friendship> getFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("ownerId", player.getUid()))
                .stream()
                .toList();
    }

    public static List<Friendship> getReverseFriends(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(Filters.eq("friendId", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveFriendship(Friendship friendship) {
        DatabaseHelper.saveGameAsync(friendship);
    }

    public static void deleteFriendship(Friendship friendship) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(friendship));
    }

    public static Friendship getReverseFriendship(Friendship friendship) {
        return DatabaseManager.getGameDatastore()
                .find(Friendship.class)
                .filter(
                        Filters.and(
                                Filters.eq("ownerId", friendship.getFriendId()),
                                Filters.eq("friendId", friendship.getOwnerId())))
                .first();
    }

    public static List<GachaRecord> getGachaRecords(int ownerId, int page, int gachaType) {
        return getGachaRecords(ownerId, page, gachaType, 10);
    }

    public static List<GachaRecord> getGachaRecords(
            int ownerId, int page, int gachaType, int pageSize) {
        return DatabaseManager.getGameDatastore()
                .find(GachaRecord.class)
                .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                .iterator(
                        new FindOptions()
                                .sort(Sort.descending("transactionDate"))
                                .skip(pageSize * page)
                                .limit(pageSize))
                .toList();
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType) {
        return getGachaRecordsMaxPage(ownerId, page, gachaType, 10);
    }

    public static long getGachaRecordsMaxPage(int ownerId, int page, int gachaType, int pageSize) {
        long count =
                DatabaseManager.getGameDatastore()
                        .find(GachaRecord.class)
                        .filter(Filters.eq("ownerId", ownerId), Filters.eq("gachaType", gachaType))
                        .count();
        return count / 10 + (count % 10 > 0 ? 1 : 0);
    }

    public static void saveGachaRecord(GachaRecord gachaRecord) {
        DatabaseHelper.saveGameAsync(gachaRecord);
    }

    public static List<Mail> getAllMail(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(Mail.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveMail(Mail mail) {
        DatabaseHelper.saveGameAsync(mail);
    }

    public static void deleteMail(Mail mail) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(mail));
    }

    public static List<GameMainQuest> getAllQuests(Player player) {
        return DatabaseManager.getGameDatastore()
                .find(GameMainQuest.class)
                .filter(Filters.eq("ownerUid", player.getUid()))
                .stream()
                .toList();
    }

    public static void saveQuest(GameMainQuest quest) {
        DatabaseHelper.saveGameAsync(quest);
    }

    public static void deleteQuest(GameMainQuest quest) {
        DatabaseHelper.asyncOperation(() -> DatabaseManager.getGameDatastore().delete(quest));
    }

    public static GameHome getHomeByUid(int id) {
        return DatabaseManager.getGameDatastore()
                .find(GameHome.class)
                .filter(Filters.eq("ownerUid", id))
                .first();
    }

    public static void saveHome(GameHome gameHome) {
        DatabaseHelper.saveGameAsync(gameHome);
    }

    public static emu.grasscutter.game.dailytask.DailyTaskManager loadDailyTaskManager(Player player) {
        var manager =
                DatabaseManager.getGameDatastore()
                        .find(emu.grasscutter.game.dailytask.DailyTaskManager.class)
                        .filter(Filters.eq("ownerUid", player.getUid()))
                        .first();

        if (manager == null) {
            manager = new emu.grasscutter.game.dailytask.DailyTaskManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }

        return manager;
    }

    public static void saveDailyTaskManager(emu.grasscutter.game.dailytask.DailyTaskManager manager) {
        runSynchronousDatabaseWrite(() -> DatabaseManager.getGameDatastore().save(manager));
    }

    public static BattlePassManager loadBattlePass(Player player) {
        BattlePassManager manager =
                DatabaseManager.getGameDatastore()
                        .find(BattlePassManager.class)
                        .filter(Filters.eq("ownerUid", player.getUid()))
                        .first();
        if (manager == null) {
            manager = new BattlePassManager(player);
            manager.save();
        } else {
            manager.setPlayer(player);
        }
        // The 7.0 client expects the current BattlePass schedule to be viewed as soon as it is loaded.
        manager.updateViewed();
        return manager;
    }

    public static void saveBattlePass(BattlePassManager manager) {
        DatabaseHelper.saveGameAsync(manager);
    }

    public static PlayerActivityData getPlayerActivityData(int uid, int activityId) {
        return DatabaseManager.getGameDatastore()
                .find(PlayerActivityData.class)
                .filter(Filters.and(Filters.eq("uid", uid), Filters.eq("activityId", activityId)))
                .first();
    }

    public static void savePlayerActivityData(PlayerActivityData playerActivityData) {
        DatabaseHelper.saveGameAsync(playerActivityData);
    }

    public static MusicGameBeatmap getMusicGameBeatmap(long musicShareId) {
        return DatabaseManager.getGameDatastore()
                .find(MusicGameBeatmap.class)
                .filter(Filters.eq("musicShareId", musicShareId))
                .first();
    }

    public static void saveMusicGameBeatmap(MusicGameBeatmap musicGameBeatmap) {
        DatabaseHelper.saveGameAsync(musicGameBeatmap);
    }

    @Nullable public static Achievements getAchievementData(int uid) {
        // Only an absent document is a new player. A decoding/read failure must not
        // erase redemption history or replace it with a fresh achievements record.
        return DatabaseManager.getGameDatastore()
                .find(Achievements.class)
                .filter(Filters.and(Filters.eq("uid", uid)))
                .first();
    }

    public static void saveAchievementData(Achievements achievements) {
        DatabaseHelper.saveGameAsync(achievements);
    }

    public static void saveGroupInstance(SceneGroupInstance instance) {
        DatabaseHelper.saveGameAsync(instance);
    }

    public static SceneGroupInstance loadGroupInstance(int groupId, Player owner) {
        return DatabaseManager.getGameDatastore()
                .find(SceneGroupInstance.class)
                .filter(Filters.and(Filters.eq("ownerUid", owner.getUid()), Filters.eq("groupId", groupId)))
                .first();
    }
}
