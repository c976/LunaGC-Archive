package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;

import emu.grasscutter.GameConstants;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.AccountUsernamePolicy;
import emu.grasscutter.game.player.Player;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;

/** Creates an independent player save snapshot under a new account and UID. */
public final class PlayerCloneService {
    private static final int MAX_UID_PROBES = 10_000;

    /**
     * Player-private collections that make up the save outside the main players document.
     *
     * <p>Friendships are deliberately excluded because cloning them would create one-sided social
     * edges to other real players. Public music-game beatmaps are excluded because their share IDs
     * and authorship are global content, not private save state.
     */
    private static final List<OwnedCollection> OWNED_COLLECTIONS =
            List.of(
                    new OwnedCollection("achievements", "uid"),
                    new OwnedCollection("activities", "uid"),
                    new OwnedCollection("homes", "ownerUid"),
                    new OwnedCollection("mail", "ownerUid"),
                    new OwnedCollection("avatars", "ownerId"),
                    new OwnedCollection("gachas", "ownerId"),
                    new OwnedCollection("items", "ownerId"),
                    new OwnedCollection("quests", "ownerUid"),
                    new OwnedCollection("battlepass", "ownerUid"),
                    new OwnedCollection("dailytasks", "ownerUid"),
                    new OwnedCollection("group_instances", "ownerUid"),
                    new OwnedCollection("open_world_spawns", "ownerUid"));

    private PlayerCloneService() {}

    public record CloneResult(int sourceUid, int targetUid, int clonedDocuments) {}

    private record OwnedCollection(String name, String ownerField) {}

    /**
     * Clones a player's persisted gameplay state into a fresh account.
     *
     * <p>The source must already be offline. The method drains asynchronous database writes before
     * reading anything, so the clone represents the fully persisted post-logout state.
     *
     * @param sourceUsername existing account username to snapshot
     * @param targetUsername new account username for the snapshot
     * @param requestedUid target UID, or 0 to allocate the next free UID
     */
    public static synchronized CloneResult cloneOffline(
            String sourceUsername, String targetUsername, int requestedUid) {
        requireUsername(sourceUsername, "Source");
        Account sourceAccount = DatabaseHelper.getAccountByName(sourceUsername);
        if (sourceAccount == null) {
            throw new IllegalArgumentException("Source account does not exist: " + sourceUsername);
        }
        return cloneOffline(sourceAccount, targetUsername, requestedUid);
    }

    /** Clone an already resolved source account, including accounts selected by player UID. */
    public static synchronized CloneResult cloneOffline(
            Account sourceAccount, String targetUsername, int requestedUid) {
        if (sourceAccount == null) {
            throw new IllegalArgumentException("Source account does not exist.");
        }
        String sourceUsername = sourceAccount.getUsername();
        requireUsername(sourceUsername, "Source");
        AccountUsernamePolicy.requireValid(targetUsername);
        if (DatabaseHelper.getAccountByName(targetUsername) != null) {
            throw new IllegalArgumentException("Target account already exists: " + targetUsername);
        }

        Player sourcePlayer = DatabaseHelper.getPlayerByAccount(sourceAccount, Player.class);
        if (sourcePlayer == null) {
            throw new IllegalStateException(
                    "Source account has no player save yet: " + sourceUsername);
        }
        ensureOffline(sourceAccount);

        if (requestedUid < 0) {
            throw new IllegalArgumentException("UID must be positive");
        }

        // Reserve the account ID before freezing other writers. UID reservations made inside the
        // barrier use the same synchronous admission gate through its owner's permit.
        String targetAccountId = Integer.toString(DatabaseManager.getNextId(Account.class));
        int targetUid = 0;

        try (DatabaseWriteBarrier ignored = DatabaseWriteBarrier.acquire()) {
            try {
                // Close the races between the initial validation and the stable snapshot window.
                ensureOffline(sourceAccount);
                if (DatabaseHelper.getAccountByName(targetUsername) != null) {
                    throw new IllegalArgumentException(
                            "Target account already exists: " + targetUsername);
                }

                targetUid =
                        requestedUid == 0 ? allocateTargetUid() : validateRequestedUid(requestedUid);
                int sourceUid = sourcePlayer.getUid();

                var database = DatabaseManager.getGameDatabase();
                Document sourcePlayerDocument =
                        database.getCollection("players").find(eq("_id", sourceUid)).first();
                if (sourcePlayerDocument == null) {
                    throw new IllegalStateException(
                            "Source player document disappeared while cloning UID " + sourceUid);
                }

                // Persist the player graph before publishing the account. Until the account document
                // exists, the snapshot cannot be logged into half-cloned.
                Document targetPlayerDocument = new Document(sourcePlayerDocument);
                targetPlayerDocument.put("_id", targetUid);
                targetPlayerDocument.put("accountId", targetAccountId);
                database.getCollection("players").insertOne(targetPlayerDocument);

                int clonedDocuments = 1;
                for (OwnedCollection owned : OWNED_COLLECTIONS) {
                    var collection = database.getCollection(owned.name());
                    for (Document source : collection.find(eq(owned.ownerField(), sourceUid))) {
                        Document target = cloneOwnedDocument(source, owned.ownerField(), targetUid);
                        collection.insertOne(target);
                        clonedDocuments++;
                    }
                }

                // If the source came back while the snapshot was being copied, refuse to publish it.
                ensureOffline(sourceAccount);

                Account targetAccount =
                        createTargetAccount(
                                sourceAccount, targetUsername, targetAccountId, targetUid);
                DatabaseManager.getAccountDatastore().save(targetAccount);

                return new CloneResult(sourceUid, targetUid, clonedDocuments);
            } catch (RuntimeException | Error failure) {
                // Keep cleanup inside the exclusive window so another writer cannot observe or
                // modify the incomplete clone between failure and rollback.
                if (targetUid > 0) {
                    try {
                        rollbackClone(targetUid, targetAccountId);
                    } catch (RuntimeException | Error rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
                throw failure;
            }
        }
    }

    static Document cloneOwnedDocument(Document source, String ownerField, int targetUid) {
        Document target = new Document(source);
        target.remove("_id");
        if (source.get("_id") instanceof String sourceId) {
            // Morphia's String @Id fields must remain BSON strings. Letting insertOne allocate an
            // ObjectId makes a later save address a different ID and can violate the owner index.
            String targetId;
            do {
                targetId = new ObjectId().toHexString();
            } while (targetId.equals(sourceId));
            target.put("_id", targetId);
        }
        target.put(ownerField, targetUid);
        return target;
    }

    private static void rollbackClone(int targetUid, String targetAccountId) {
        var database = DatabaseManager.getGameDatabase();
        var players = database.getCollection("players");
        Document targetPlayer =
                players
                        .find(
                                com.mongodb.client.model.Filters.and(
                                        eq("_id", targetUid), eq("accountId", targetAccountId)))
                        .first();

        // Only remove UID-owned data when the player document proves this clone owns that UID.
        // If another writer won a UID race before our player insert, leave its data untouched.
        if (targetPlayer != null) {
            for (OwnedCollection owned : OWNED_COLLECTIONS) {
                database
                        .getCollection(owned.name())
                        .deleteMany(eq(owned.ownerField(), targetUid));
            }
            players.deleteOne(
                    com.mongodb.client.model.Filters.and(
                            eq("_id", targetUid), eq("accountId", targetAccountId)));
        }

        DatabaseManager.getAccountDatastore()
                .find(Account.class)
                .filter(dev.morphia.query.experimental.filters.Filters.eq("_id", targetAccountId))
                .delete();
    }

    private static Account createTargetAccount(
            Account source, String username, String accountId, int targetUid) {
        var target = new Account();
        target.setId(accountId);
        target.setUsername(username);
        target.setReservedPlayerUid(targetUid);
        target.setPassword(source.getPassword());
        target.setLocale(source.getLocale());

        for (String permission : source.getPermissions()) {
            target.addPermission(permission);
        }
        return target;
    }

    private static int allocateTargetUid() {
        int candidate = PlayerUidAllocator.next(0);
        for (int probe = 0; probe < MAX_UID_PROBES; probe++) {
            if (isUidFree(candidate)) {
                return candidate;
            }
            if (candidate == Integer.MAX_VALUE) {
                break;
            }
            candidate++;
        }
        throw new IllegalStateException("Could not find a free UID for the cloned player");
    }

    private static int validateRequestedUid(int uid) {
        if (uid <= 0) {
            throw new IllegalArgumentException("UID must be positive");
        }
        if (uid == GameConstants.SERVER_CONSOLE_UID) {
            throw new IllegalArgumentException("UID is reserved for the server console: " + uid);
        }
        if (!isUidFree(uid)) {
            throw new IllegalArgumentException("UID is already in use or reserved: " + uid);
        }
        return uid;
    }

    private static boolean isUidFree(int uid) {
        return uid != GameConstants.SERVER_CONSOLE_UID
                && !DatabaseHelper.checkIfAccountExists(uid)
                && !DatabaseHelper.checkIfPlayerExists(uid);
    }

    private static void ensureOffline(Account account) {
        if (Grasscutter.getGameServer().getPlayerByAccountId(account.getId()) != null) {
            throw new IllegalStateException(
                    "Source account must be offline before cloning: " + account.getUsername());
        }
    }

    private static void requireUsername(String username, String role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(role + " account username cannot be blank");
        }
    }
}
