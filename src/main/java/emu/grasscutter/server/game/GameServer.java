package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.*;
import static emu.grasscutter.utils.lang.Language.translate;

import emu.grasscutter.*;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.game.battlepass.BattlePassSystem;
import emu.grasscutter.game.chat.ChatSystem;
import emu.grasscutter.game.chat.ChatSystemHandler;
import emu.grasscutter.game.combine.CombineManger;
import emu.grasscutter.game.drop.DropSystem;
import emu.grasscutter.game.drop.DropSystemLegacy;
import emu.grasscutter.game.dungeons.DungeonSystem;
import emu.grasscutter.game.expedition.ExpeditionSystem;
import emu.grasscutter.game.gacha.GachaSystem;
import emu.grasscutter.game.home.HomeWorld;
import emu.grasscutter.game.home.HomeWorldMPSystem;
import emu.grasscutter.game.managers.cooking.CookingCompoundManager;
import emu.grasscutter.game.managers.cooking.CookingManager;
import emu.grasscutter.game.managers.energy.EnergyManager;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.QuestSystem;
import emu.grasscutter.game.shop.ShopSystem;
import emu.grasscutter.game.systems.AnnouncementSystem;
import emu.grasscutter.game.systems.InventorySystem;
import emu.grasscutter.game.systems.MultiplayerSystem;
import emu.grasscutter.game.talk.TalkSystem;
import emu.grasscutter.game.tower.TowerSystem;
import emu.grasscutter.game.world.World;
import emu.grasscutter.game.world.WorldDataSystem;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.ProfilePictureOuterClass.ProfilePicture;
import emu.grasscutter.net.proto.SocialDetailOuterClass.SocialDetail;
import emu.grasscutter.server.dispatch.DispatchClient;
import emu.grasscutter.server.ServerWatchdog;
import emu.grasscutter.server.event.game.ServerTickEvent;
import emu.grasscutter.server.event.internal.ServerStartEvent;
import emu.grasscutter.server.event.internal.ServerStopEvent;
import emu.grasscutter.server.event.types.ServerEvent;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import emu.grasscutter.task.TaskMap;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import kcp.highway.*;
import lombok.*;
import org.jetbrains.annotations.*;

@Getter
public final class GameServer extends KcpServer implements Iterable<Player> {
    // Game server base
    private final InetSocketAddress address;
    private ChannelConfig channelConfig;
    private final GameServerPacketHandler packetHandler;
    private final Map<Integer, Player> players;
    private final Set<World> worlds;
    private final Int2ObjectMap<HomeWorld> homeWorlds;
    private volatile long completedTickCount;
    private volatile long lastTickCompletedAtMillis;
    private volatile long lastTickCompletedNanos;
    private volatile long lastTickDurationMillis;
    private volatile long maxTickDurationMillis;
    private Timer gameLoop;

    @Setter private DispatchClient dispatchClient;

    // Server systems
    private final InventorySystem inventorySystem;
    private final GachaSystem gachaSystem;
    private final ShopSystem shopSystem;
    private final MultiplayerSystem multiplayerSystem;
    private final HomeWorldMPSystem homeWorldMPSystem;
    private final DungeonSystem dungeonSystem;
    private final ExpeditionSystem expeditionSystem;
    private final DropSystem dropSystem;
    private final DropSystemLegacy dropSystemLegacy;
    private final WorldDataSystem worldDataSystem;
    private final BattlePassSystem battlePassSystem;
    private final CombineManger combineSystem;
    private final TowerSystem towerSystem;
    private final AnnouncementSystem announcementSystem;
    private final QuestSystem questSystem;
    private final TalkSystem talkSystem;

    // Extra
    private final ServerTaskScheduler scheduler;
    private final TaskMap taskMap;

    private ChatSystemHandler chatManager;

    /**
     * @return The URI for the dispatch server.
     */
    @SneakyThrows
    public static URI getDispatchUrl() {
        return new URI(DISPATCH_INFO.dispatchUrl);
    }

    public GameServer() {
        this(getAdapterInetSocketAddress());
    }

    public GameServer(InetSocketAddress address) {
        // Check if we are in dispatch only mode.
        if (Grasscutter.getRunMode() == ServerRunMode.DISPATCH_ONLY) {
            // Set all the systems to null.
            this.scheduler = null;
            this.taskMap = null;

            this.address = null;
            this.packetHandler = null;
            this.dispatchClient = null;
            this.players = null;
            this.worlds = null;
            this.homeWorlds = null;

            this.inventorySystem = null;
            this.gachaSystem = null;
            this.shopSystem = null;
            this.multiplayerSystem = null;
            this.homeWorldMPSystem = null;
            this.dungeonSystem = null;
            this.expeditionSystem = null;
            this.dropSystem = null;
            this.dropSystemLegacy = null;
            this.worldDataSystem = null;
            this.battlePassSystem = null;
            this.combineSystem = null;
            this.towerSystem = null;
            this.announcementSystem = null;
            this.questSystem = null;
            this.talkSystem = null;
            return;
        }

        var channelConfig = new ChannelConfig();
        channelConfig.nodelay(true, GAME_INFO.kcpInterval, 2, true);
        channelConfig.setMtu(1400);
        channelConfig.setSndwnd(256);
        channelConfig.setRcvwnd(256);
        channelConfig.setTimeoutMillis(30 * 1000); // 30s
        channelConfig.setUseConvChannel(true);
        channelConfig.setAckNoDelay(false);

        // The socket is bound in start(), after the resources have loaded. Bound here, it took
        // logins while the avatar tables were still empty, and every client reconnecting the moment
        // the server restarted failed with a NullPointerException creating its Traveler.
        this.channelConfig = channelConfig;

        EnergyManager.initialize();
        StaminaManager.initialize();
        CookingManager.initialize();
        CookingCompoundManager.initialize();
        CombineManger.initialize();

        // Game Server base
        this.address = address;
        this.packetHandler = new GameServerPacketHandler(PacketHandler.class);
        this.dispatchClient = new DispatchClient(GameServer.getDispatchUrl());
        this.players = new ConcurrentHashMap<>();
        this.worlds = Collections.synchronizedSet(new HashSet<>());
        this.homeWorlds = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

        // Extra
        this.scheduler = new ServerTaskScheduler();
        this.taskMap = new TaskMap(true);

        // Create game systems
        this.inventorySystem = new InventorySystem(this);
        this.gachaSystem = new GachaSystem(this);
        this.shopSystem = new ShopSystem(this);
        this.multiplayerSystem = new MultiplayerSystem(this);
        this.homeWorldMPSystem = new HomeWorldMPSystem(this);
        this.dungeonSystem = new DungeonSystem(this);
        this.dropSystem = new DropSystem(this);
        this.dropSystemLegacy = new DropSystemLegacy(this);
        this.expeditionSystem = new ExpeditionSystem(this);
        this.combineSystem = new CombineManger(this);
        this.towerSystem = new TowerSystem(this);
        this.worldDataSystem = new WorldDataSystem(this);
        this.battlePassSystem = new BattlePassSystem(this);
        this.announcementSystem = new AnnouncementSystem(this);
        this.questSystem = new QuestSystem(this);
        this.talkSystem = new TalkSystem(this);

        // Chata manager
        this.chatManager = new ChatSystem(this);
    }

    private static InetSocketAddress getAdapterInetSocketAddress() {
        InetSocketAddress inetSocketAddress;
        if (GAME_INFO.bindAddress.equals("")) {
            inetSocketAddress = new InetSocketAddress(GAME_INFO.bindPort);
        } else {
            inetSocketAddress = new InetSocketAddress(GAME_INFO.bindAddress, GAME_INFO.bindPort);
        }
        return inetSocketAddress;
    }

    @Deprecated
    public ChatSystemHandler getChatManager() {
        return chatManager;
    }

    @Deprecated
    public void setChatManager(ChatSystemHandler chatManager) {
        this.chatManager = chatManager;
    }

    public ChatSystemHandler getChatSystem() {
        return chatManager;
    }

    public void setChatSystem(ChatSystemHandler chatManager) {
        this.chatManager = chatManager;
    }

    public void registerPlayer(Player player) {
        getPlayers().put(player.getUid(), player);
    }

    @Nullable public Player getPlayerByUid(int id) {
        return this.getPlayerByUid(id, false);
    }

    @Nullable public Player getPlayerByUid(int id, boolean allowOfflinePlayers) {
        // Console / bot accounts are not real players
        if (GameConstants.isServerBotUid(id)) {
            return null;
        }

        // Get from online players
        Player player = this.getPlayers().get(id);

        if (!allowOfflinePlayers) {
            return player;
        }

        // Check database if character isnt here
        if (player == null) {
            player = DatabaseHelper.getPlayerByUid(id);
        }

        return player;
    }

    public Player getPlayerByAccountId(String accountId) {
        Optional<Player> playerOpt =
                getPlayers().values().stream()
                        .filter(player -> player.getAccount().getId().equals(accountId))
                        .findFirst();
        return playerOpt.orElse(null);
    }

    /**
     * Tries to find a player with the matching IP address.
     *
     * @param ipAddress The IP address. This should just be numbers without a port.
     * @return The player, or null if one could not be found.
     */
    public Player getPlayerByIpAddress(String ipAddress) {
        return this.getPlayers().values().stream()
                .map(Player::getSession)
                .filter(
                        session -> session != null && session.getAddress().getHostString().equals(ipAddress))
                .map(GameSession::getPlayer)
                .findFirst()
                .orElse(null);
    }

    public SocialDetail.Builder getSocialDetailByUid(int id) {
        if (GameConstants.isServerBotUid(id)) {
            var serverAccount =
                    id == GameConstants.SERVER_DPS_UID && GAME_INFO.dpsAccount != null
                            ? GAME_INFO.dpsAccount
                            : GAME_INFO.serverAccount;
            return SocialDetail.newBuilder()
                    .setUid(id)
                    .setProfilePicture(ProfilePicture.newBuilder().setAvatarId(serverAccount.avatarId))
                    .setNickname(serverAccount.nickName)
                    .setSignature(serverAccount.signature)
                    .setLevel(serverAccount.adventureRank)
                    .setWorldLevel(serverAccount.worldLevel)
                    .setNameCardId(serverAccount.nameCardId)
                    .setIsShowAvatar(false)
                    .setFinishAchievementNum(0)
                    .setFriendEnterHomeOptionValue(0);
        }

        // Get from online players
        Player player = this.getPlayerByUid(id, true);

        if (player == null) {
            return null;
        }

        return player.getSocialDetail();
    }

    public Account getAccountByName(String username) {
        Optional<Player> playerOpt =
                getPlayers().values().stream()
                        .filter(player -> player.getAccount().getUsername().equals(username))
                        .findFirst();
        if (playerOpt.isPresent()) {
            return playerOpt.get().getAccount();
        }
        return DatabaseHelper.getAccountByName(username);
    }

    public synchronized void onTick() {
        // Nothing that happens in a tick can be recorded while the database is unreachable, and
        // every save it would queue just piles up behind a connection that is not coming back.
        // The watchdog clears this as soon as the database answers again.
        if (ServerWatchdog.isDatabaseDown()) return;

        long tickStartedNanos = System.nanoTime();
        boolean completed = false;
        try {
            var tickStart = Instant.now();

            // Each of these is guarded on its own. One world or one player throwing used to abandon
            // the whole tick, so everybody else's world stopped moving - and the scheduler at the
            // end never ran at all. Collection locks are only held while copying, never while
            // calling back into a world or player that may need the same caches in reverse order.
            final List<World> worldSnapshot;
            synchronized (this.worlds) {
                worldSnapshot = new ArrayList<>(this.worlds);
            }
            for (World world : worldSnapshot) {
                if (!this.worlds.contains(world)) continue;
                try {
                    if (world.onTick()) {
                        this.removeWorldIfEmpty(world);
                    }
                } catch (Throwable e) {
                    Grasscutter.getLogger().error("A world threw while ticking.", e);
                }
            }

            this.players
                    .values()
                    .forEach(
                            player -> {
                                try {
                                    player.onTick();
                                } catch (Throwable e) {
                                    Grasscutter.getLogger().error("Player {} threw while ticking.", player.getUid(), e);
                                }
                            });

            try {
                this.getScheduler().runTasks();
            } catch (Throwable e) {
                Grasscutter.getLogger().error("A scheduled task threw.", e);
            }

            // Call server tick event.
            ServerTickEvent event = new ServerTickEvent(tickStart, Instant.now());
            event.call();
            completed = true;
        } finally {
            long finishedNanos = System.nanoTime();
            long duration = TimeUnit.NANOSECONDS.toMillis(finishedNanos - tickStartedNanos);
            this.lastTickDurationMillis = duration;
            this.maxTickDurationMillis = Math.max(this.maxTickDurationMillis, duration);
            if (completed) {
                this.lastTickCompletedAtMillis = System.currentTimeMillis();
                this.lastTickCompletedNanos = finishedNanos;
                // Publish completion after its timestamps. Status reads must not take the tick lock.
                this.completedTickCount++;
            }
        }
    }

    /** Complete loops, including the tick event; isolated world/player errors do not stop progress. */
    public long getCompletedTickCount() {
        return this.completedTickCount;
    }

    public long getLastTickCompletedAtMillis() {
        return this.lastTickCompletedAtMillis;
    }

    public long getLastTickCompletedNanos() {
        return this.lastTickCompletedNanos;
    }

    public long getLastTickDurationMillis() {
        return this.lastTickDurationMillis;
    }

    public long getMaxTickDurationMillis() {
        return this.maxTickDurationMillis;
    }

    public void registerWorld(World world) {
        this.getWorlds().add(world);
    }

    public void deregisterWorld(World world) {
        if (world == null) {
            return;
        }
        this.worlds.remove(world);
        if (world instanceof HomeWorld homeWorld) {
            Player host = homeWorld.getHost();
            if (host != null) {
                this.homeWorlds.remove(host.getUid(), homeWorld);
            }
        }
        world.save(); // Save the player's world
    }

    public HomeWorld getHomeWorldOrCreate(Player owner) {
        return this.getHomeWorlds()
                .computeIfAbsent(owner.getUid(), (uid) -> new HomeWorld(this, owner));
    }

    /** Removes an empty home world without deleting a newer cached instance for its host. */
    public void releaseHomeWorldIfEmpty(HomeWorld homeWorld) {
        if (homeWorld != null) {
            this.removeWorldIfEmpty(homeWorld);
        }
    }

    private void removeWorldIfEmpty(World world) {
        // addPlayer/removePlayer use the world monitor. Recheck after onTick so a player who
        // joined after the snapshot cannot lose their world to an earlier empty-world result.
        synchronized (world) {
            if (world.getPlayerCount() != 0) return;
            if (world instanceof HomeWorld homeWorld) {
                Player host = homeWorld.getHost();
                if (host != null) {
                    this.homeWorlds.remove(host.getUid(), homeWorld);
                }
            }
            this.worlds.remove(world);
        }
    }

    public void start() {
        this.init(GameSessionManager.getListener(), this.channelConfig, this.address);

        if (Grasscutter.getRunMode() == ServerRunMode.GAME_ONLY) {
            // Connect to dispatch server.
            this.dispatchClient.connect();
        }

        // Settle which Spiral Abyss rotation is live now that the resources are in. Doing it here
        // rather than on the first abyss screen means a resource set that cannot build any rotation
        // says so at boot, not to whoever opens the abyss first.
        this.announceTowerRotation();

        // Schedule game loop.
        gameLoop = new Timer();
        gameLoop.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            onTick();
                        } catch (Throwable e) {
                            // A Timer thread dies on anything it does not catch, and it is the only
                            // thing driving the world - so the game would simply stop, quietly.
                            Grasscutter.getLogger().error(translate("messages.game.game_update_error"), e);
                        }
                    }
                },
                new Date(),
                Math.max(1, GAME_INFO.tickRateMs));
        Grasscutter.getLogger().info(translate("messages.status.free_software"));
        Grasscutter.getLogger()
                .info(translate("messages.game.address_bind", GAME_INFO.accessAddress, address.getPort()));
        ServerStartEvent event = new ServerStartEvent(ServerEvent.Type.GAME, OffsetDateTime.now());
        event.call();
    }

    private void announceTowerRotation() {
        try {
            var schedule = this.towerSystem.getCurrentTowerScheduleData();
            if (schedule == null) return;

            var floors = this.towerSystem.getScheduleFloors();
            Grasscutter.getLogger()
                    .info(
                            "Spiral Abyss: rotation {} is live, floors 9-12 are {}, next change {}.",
                            schedule.getScheduleId(),
                            floors,
                            this.towerSystem.getNextScheduleChangeTime());
        } catch (Throwable e) {
            Grasscutter.getLogger().error("Could not settle the Spiral Abyss rotation.", e);
        }
    }

    /** Saves every player and world before the transport stops. Leaves any interrupt set. */
    public void onServerShutdown() {
        GameSessionManager.closeAdmission();
        if (gameLoop != null) {
            gameLoop.cancel();
            synchronized (this) {} // Waits for a running tick; onTick is synchronized.
        }
        Utils.runAll(
                "Server stop event",
                () -> new ServerStopEvent(ServerEvent.Type.GAME, OffsetDateTime.now()).call());

        // Log players out on the logic thread, which owns their state; the writer drain saves them.
        boolean interrupted = false;
        var logicThread = GameSessionManager.getLogicThread();
        try {
            logicThread
                    .submit(
                            () -> {
                                var sessions =
                                        this.getPlayers().values().stream()
                                                .map(Player::getSession)
                                                .filter(Objects::nonNull)
                                                .toList();
                                sessions.forEach(
                                        session -> Utils.runAll("Logout", session::close, session::handleClose));
                            })
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            interrupted = true;
        } catch (ExecutionException | TimeoutException e) {
            Grasscutter.getLogger().error("Not every player was logged out before shutdown.", e);
        }
        this.getWorlds().forEach(world -> Utils.runAll("World save", world::save));

        this.stop(); // Stop the server.
        logicThread.shutdownGracefully();
        try {
            if (!logicThread.awaitTermination(5, TimeUnit.SECONDS)) {
                Grasscutter.getLogger().error("Logic thread did not terminate!");
            }
        } catch (InterruptedException e) {
            interrupted = true;
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    @NotNull @Override
    public Iterator<Player> iterator() {
        return this.getPlayers().values().iterator();
    }
}
