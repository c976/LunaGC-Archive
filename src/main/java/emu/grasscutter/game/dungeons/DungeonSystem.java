package emu.grasscutter.game.dungeons;

import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.data.excels.dungeon.*;
import emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager;
import emu.grasscutter.game.dungeons.handlers.DungeonBaseHandler;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tower.TowerAbyssFix;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketDungeonEntryInfoRsp;
import it.unimi.dsi.fastutil.ints.*;
import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import emu.grasscutter.net.proto.Uint32PairOuterClass.Uint32Pair;

public final class DungeonSystem extends BaseGameSystem {
    private static final BasicDungeonSettleListener basicDungeonSettleObserver =
            new BasicDungeonSettleListener();
    private final Int2ObjectMap<DungeonBaseHandler> passCondHandlers;

    public DungeonSystem(GameServer server) {
        super(server);

        this.passCondHandlers = new Int2ObjectOpenHashMap<>();
        this.registerHandlers();
    }

    public void registerHandlers() {
        this.registerHandlers(this.passCondHandlers, DungeonBaseHandler.class);
    }

    public <T> void registerHandlers(Int2ObjectMap<T> map, Class<T> clazz) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(clazz);
        for (var obj : handlerClasses) {
            this.registerHandler(map, obj);
        }
    }

    public <T> void registerHandler(Int2ObjectMap<T> map, Class<? extends T> handlerClass) {
        try {
            DungeonValue opcode = handlerClass.getAnnotation(DungeonValue.class);

            if (opcode == null || opcode.value() == null) {
                return;
            }

            map.put(opcode.value().ordinal(), handlerClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends the entry info for the given dungeon point to the player.
     *
     * @param player The player to send the entry info to.
     * @param pointId The dungeon point ID.
     */
    public void sendEntryInfoFor(Player player, int pointId, int sceneId) {
        sendEntryInfoFor(player,
                List.of(Uint32Pair.newBuilder().setKey(sceneId).setValue(pointId).build()), false);
    }

    public void sendEntryInfoFor(
            Player player, Collection<Uint32Pair> scenePoints, boolean entryList) {
        var entries = new LinkedHashMap<ScenePointEntry, List<Integer>>();
        for (var scenePoint : new java.util.LinkedHashSet<>(scenePoints)) {
            int sceneId = scenePoint.getKey() > 0 ? scenePoint.getKey() : player.getSceneId();
            var entry = GameData.getScenePointEntryById(sceneId, scenePoint.getValue());
            if (entry == null || !"DungeonEntry".equals(entry.getPointData().getType())) continue;
            entries.put(entry, player.getQuestManager().questsForDungeon(entry));
        }
        player.sendPacket(new PacketDungeonEntryInfoRsp(entries, player.getLevel(), entryList));
    }

    public boolean triggerCondition(
            DungeonPassConfigData.DungeonPassCondition condition, int... params) {
        var handler = passCondHandlers.get(condition.getCondType().ordinal());

        if (handler == null) {
            Grasscutter.getLogger()
                    .debug("Could not trigger condition {} at {}", condition.getCondType(), params);
            return false;
        }

        return handler.execute(condition, params);
    }

    public boolean enterDungeon(Player player, int pointId, int dungeonId, boolean savePrevious) {
        DungeonData data = GameData.getDungeonDataMap().get(dungeonId);

        if (data == null) {
            return false;
        }
        Grasscutter.getLogger()
                .debug(
                        "{} ({}) is trying to enter dungeon {}.",
                        player.getNickname(),
                        player.getUid(),
                        dungeonId);

        var sceneId = data.getSceneId();
        var origin = player.getScene();
        var originPos = new Position(player.getPosition());
        if (!player.getWorld().transferPlayerToScene(player, sceneId, data)) {
            return false;
        }
        var scene = player.getScene();
        // The scene's own origin dies with it, and a dungeon scene is deregistered as soon as the
        // last player leaves - so keep the origin on the player, who does survive the logout.
        // Player.onLogin() reads this pair instead of restoring a login into an empty dungeon
        // shell. Only world scenes qualify, so a home or activity handoff cannot record one.
        if (origin != null && origin.getSceneType() == SceneType.SCENE_WORLD) {
            player.setPrevSceneForDungeon(origin.getId());
            player.setPrevPosForDungeon(originPos);
        }
        if (savePrevious && origin != null) {
            scene.setPrevScene(
                    origin.getSceneType() == SceneType.SCENE_WORLD ? origin.getId() : origin.getPrevScene());
        }
        scene.setDungeonManager(new DungeonManager(scene, data));
        MissingDomainFallbackManager.install(scene, data);
        scene.addDungeonSettleObserver(basicDungeonSettleObserver);
        if (DomainDungeonHelper.isDomainScene(scene)) {
            DomainDungeonHelper.onPlayerEnterDomain(player);
        }

        if (savePrevious) scene.setPrevScenePoint(pointId);
        return true;
    }

    /** used in tower dungeons handoff */
    public boolean handoffDungeon(
            Player player, int dungeonId, List<DungeonSettleListener> dungeonSettleListeners) {
        DungeonData data = GameData.getDungeonDataMap().get(dungeonId);

        if (data == null) {
            return false;
        }
        Grasscutter.getLogger()
                .info(
                        "{}({}) is trying to enter tower dungeon {}",
                        player.getNickname(),
                        player.getUid(),
                        dungeonId);

        Scene current = player.getScene();
        if (current != null
                && current.getSceneType() == SceneType.SCENE_DUNGEON
                && (TowerAbyssFix.isInTowerDungeon(player)
                        || (current.getDungeonManager() != null
                                && current.getDungeonManager().isTowerDungeon()))) {
            TowerAbyssFix.endChallengeUi(player);
        }

        if (!player.getWorld().transferPlayerToScene(player, data.getSceneId(), data)) {
            return false;
        }
        var scene = player.getScene();
        var dungeonManager = new DungeonManager(scene, data);
        dungeonManager.setTowerDungeon(true);
        scene.setDungeonManager(dungeonManager);
        dungeonSettleListeners.forEach(scene::addDungeonSettleObserver);
        return true;
    }

    public void exitDungeon(Player player) {
        Scene scene = player.getScene();

        if (scene == null || scene.getSceneType() != SceneType.SCENE_DUNGEON) {
            return;
        }

        // Get previous scene
        int prevScene = scene.getPrevScene() > 0 ? scene.getPrevScene() : 3;

        // Get previous position
        DungeonManager dungeonManager = scene.getDungeonManager();
        DungeonData dungeonData = dungeonManager != null ? dungeonManager.getDungeonData() : null;
        Position prevPos = new Position(GameConstants.START_POSITION);
        boolean towerDungeon =
                (dungeonManager != null && dungeonManager.isTowerDungeon())
                        || TowerAbyssFix.isInTowerDungeon(player);

        if (towerDungeon) {
            prevScene = TowerAbyssFix.resolveExitScene(player, prevScene);
            prevPos = TowerAbyssFix.resolveExitPosition(player, prevScene, scene.getPrevScenePoint());
            player.getRotation().set(TowerAbyssFix.resolveExitRotation(player));
        }

        if (dungeonData != null) {
            ScenePointEntry entry = GameData.getScenePointEntryById(prevScene, scene.getPrevScenePoint());

            if (!towerDungeon && entry != null) {
                prevPos.set(entry.getPointData().getTranPos());
            }
            if (!dungeonManager.isFinishedSuccessfully()) {
                dungeonManager.quitDungeon();
            }

            dungeonManager.unsetTrialTeam(player);
        }
        // clean temp team if it has
        if (!player.getTeamManager().cleanTemporaryTeam()) {
            // no temp team. Will use real current team, but check
            // for any dead avatar to prevent switching into them.
            player.getTeamManager().checkCurrentAvatarIsAlive(null);
        }
        if (towerDungeon) {
            TowerAbyssFix.endChallengeUi(player);
            TowerAbyssFix.clearEntry(player);
            player.getTowerManager().clearAbyssResume();
            player.getTowerManager().clearLevelEntityConfigs();
            // Finishing a floor advances CurLevelRecord to the next floor; without clearing it the
            // abyss UI thinks a run is still active and blocks reward claiming with the "continue challenge?" prompt.
            player.sendPacket(
                    emu.grasscutter.server.packet.send.PacketTowerCurLevelRecordChangeNotify.empty());
        }
        player.getTowerManager().clearEntry();
        if (dungeonManager != null) {
            dungeonManager.setTowerDungeon(false);
        }

        // Transfer player back to world after a small delay.
        // This wait is important for avoiding double teleports,
        // which specifically happen when player quits a dungeon
        // by teleporting to map waypoints.
        // From testing, 200ms seem reasonable.
        player.getWorld().queueTransferPlayerToScene(player, prevScene, prevPos, 200);
    }

    public void restartDungeon(Player player) {
        var scene = player.getScene();
        if (scene == null) {
            return;
        }
        var dungeonManager = scene.getDungeonManager();
        if (dungeonManager == null || dungeonManager.getDungeonData() == null) {
            Grasscutter.getLogger().warn("Dungeon restart ignored uid={} scene={}: no active dungeon manager",
                    player.getUid(), scene.getId());
            return;
        }
        if (!scene.getPlayers().contains(player)) {
            Grasscutter.getLogger().warn("Dungeon restart ignored uid={} scene={}: player is not in scene",
                    player.getUid(), scene.getId());
            return;
        }

        // The grant/charge critical section uses this exact DungeonManager monitor.
        // Scene teardown must not pass a still-running claim on the old instance.
        synchronized (dungeonManager) {
            if (player.getScene() != scene || scene.getDungeonManager() != dungeonManager) {
                return;
            }
            dungeonManager.restartDungeon();
        var dungeonData = dungeonManager.getDungeonData();
        var sceneId = dungeonData.getSceneId();
        var isTower = dungeonManager.isTowerDungeon();

        if (isTower || TowerAbyssFix.isInTowerDungeon(player)) {
            TowerAbyssFix.endChallengeUi(player);
            var tower = player.getTowerManager();
            tower.onEnd();
            tower.restartCurrentChamber();
        }

        // Preserve the exit destination before destroying the old scene.
        var prevScene = scene.getPrevScene();
        var pointId = scene.getPrevScenePoint();

        // Removing players mutates scene.getPlayers(). Iterating over that live collection
        // throws ConcurrentModificationException before a replacement scene can be created.
        // Snapshot and detach everyone first; then transfer all participants into the new run.
        scene.setDontDestroyWhenEmpty(false);
        var participants = DungeonRestartParticipants.detachAll(
                scene.getPlayers(), scene::removePlayer);
        Scene restartedScene = null;
        int restored = 0;
        for (var participant : participants) {
            if (!participant.getWorld().transferPlayerToScene(participant, sceneId, dungeonData)) {
                Grasscutter.getLogger().warn(
                        "Dungeon restart transfer failed uid={} dungeon={} scene={}",
                        participant.getUid(), dungeonData.getId(), sceneId);
                continue;
            }
            var joinedScene = participant.getScene();
            if (restartedScene == null) {
                restartedScene = joinedScene;
                restartedScene.setPrevScene(prevScene);
                restartedScene.setPrevScenePoint(pointId);
                var newManager = new DungeonManager(restartedScene, dungeonData);
                newManager.setTowerDungeon(isTower);
                restartedScene.setDungeonManager(newManager);
                if (isTower) {
                    restartedScene.addDungeonSettleObserver(new TowerDungeonSettleListener());
                } else {
                    MissingDomainFallbackManager.install(restartedScene, dungeonData);
                    restartedScene.addDungeonSettleObserver(basicDungeonSettleObserver);
                }
            }
            if (isTower) {
                TowerAbyssFix.prepareFirstChamber(participant);
                participant.getTowerManager().notifyCurLevelRecordChange();
            } else if (DomainDungeonHelper.isDomainScene(joinedScene)) {
                DomainDungeonHelper.onPlayerEnterDomain(participant);
            }
            restored++;
        }
        Grasscutter.getLogger().info(
                "Dungeon restart uid={} dungeon={} scene={} restored={}/{}",
                player.getUid(), dungeonData.getId(), sceneId, restored, participants.size());
        } // dungeonManager: same lock as DomainDropSafety.commitOnce
    }

}
