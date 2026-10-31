package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.EnvAnimalGatherConfigData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.DungeonSystem;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.TestWorld.TestPlayer;
import emu.grasscutter.game.world.TestWorld.TestScene;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.utils.JsonUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class WorldQuestFixesTest {
    private static final int DUNGEON = 990_001;
    private static final int DUNGEON_SCENE = 990_002;
    private static final int GADGET = 990_003;
    private static final int ANIMAL = 990_004;
    private static boolean bigWorldScripts;
    private TestPlayer player;
    private TestWorld world;
    private TestScene scene;

    @BeforeAll
    static void addResources() throws Exception {
        ScriptLoader.init();
        bigWorldScripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        GameData.getDungeonDataMap()
                .put(DUNGEON, decode(DungeonData.class, "{id:%d,sceneId:%d,limitLevel:30}", DUNGEON, DUNGEON_SCENE));
        GameData.getGadgetDataMap().put(GADGET, decode(GadgetData.class, "{id:%d,jsonName:'Test'}", GADGET));
        GameData.getEnvAnimalGatherConfigDataMap()
                .put(ANIMAL, decode(EnvAnimalGatherConfigData.class, "{animalId:%d,gatherItemList:[{id:1,count:1}]}", ANIMAL));
    }

    @AfterAll
    static void removeResources() {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = bigWorldScripts;
        GameData.getDungeonDataMap().remove(DUNGEON);
        GameData.getGadgetDataMap().remove(GADGET);
        GameData.getEnvAnimalGatherConfigDataMap().remove(ANIMAL);
    }

    @BeforeEach
    void createWorld() {
        player = new TestPlayer();
        world = TestWorld.create(player);
        scene = new TestScene(world, 3, SceneType.SCENE_WORLD);
        player.setScene(scene);
    }

    @Test
    void rejectedTransferKeepsThePendingTeleport() {
        var pending = new CompletableFuture<Void>();
        player.setQueuedTeleport(pending);

        var unknownScene =
                TeleportProperties.builder().sceneId(990_999).teleportType(TeleportType.WAYPOINT).build();
        assertFalse(world.transferPlayerToScene(player, unknownScene));

        assertSame(pending, player.getQueuedTeleport());
        assertFalse(pending.isCancelled());
    }

    @Test
    void lockedTimeIsNotCountedAfterUnlock() throws Exception {
        long before = world.getWorldTime();
        world.lockTime(true);
        Thread.sleep(300);
        world.lockTime(false);

        assertTrue(world.getWorldTime() - before < 200, "locked time leaked into world time");
    }

    @Test
    void unloadingAGroupWithoutABlockDoesNotThrow() {
        assertDoesNotThrow(() -> scene.unloadGroup(null, 7));
    }

    @Test
    void rejectedDungeonEntryKeepsTheReconnectOrigin() {
        world.transferResult = false;
        var dungeons = new DungeonSystem(null);

        assertFalse(dungeons.enterDungeon(player, 1, DUNGEON, true));
        assertFalse(dungeons.handoffDungeon(player, DUNGEON, List.of()));
        assertEquals(0, player.getPrevSceneForDungeon());
    }

    @Test
    void openWorldEntryChecksLevelAndSameSceneReentry() {
        player.level = 20;
        assertFalse(DomainDungeonHelper.canEnterDungeonFromOpenWorld(player, 5, DUNGEON));

        player.level = 60;
        var dungeonScene = new TestScene(world, DUNGEON_SCENE, SceneType.SCENE_DUNGEON);
        dungeonScene.setDungeonManager(new DungeonManager(dungeonScene, GameData.getDungeonDataMap().get(DUNGEON)));
        dungeonScene.setPrevScenePoint(5);
        player.setScene(dungeonScene);
        assertTrue(DomainDungeonHelper.canEnterDungeonFromOpenWorld(player, 5, DUNGEON));
        assertFalse(DomainDungeonHelper.canEnterDungeonFromOpenWorld(player, 6, DUNGEON));
    }

    @Test
    void finishedDungeonSettlesOnce() {
        var dungeonScene = new TestScene(world, DUNGEON_SCENE, SceneType.SCENE_DUNGEON);
        var manager = new DungeonManager(dungeonScene, GameData.getDungeonDataMap().get(DUNGEON));
        int[] settles = {0};
        dungeonScene.addDungeonSettleObserver((dungeon, reason) -> settles[0]++);

        manager.finishDungeon();
        manager.finishDungeon();

        assertEquals(1, settles[0]);
    }

    @Test
    void gadgetInteractionIsCreditedOnlyWhenEnabled() {
        var gadget = new EntityGadget(scene, GADGET, new Position());
        var request = GadgetInteractReq.newBuilder().setGadgetId(GADGET).build();

        gadget.setInteractEnabled(false);
        gadget.onInteract(player, request);
        assertTrue(player.interactedGadgets.isEmpty());

        gadget.setInteractEnabled(true);
        gadget.onInteract(player, request);
        assertEquals(List.of(GADGET), player.interactedGadgets);
    }

    @Test
    void animalGatherRewardsAndCreditsOnce() {
        var animal = animal();
        var request = GadgetInteractReq.newBuilder().setGadgetId(GADGET).build();

        player.addItemResult = false;
        animal.onInteract(player, request);
        assertFalse(animal.isEnvGatherRewarded(), "a full bag must leave the animal gatherable");
        assertTrue(player.interactedGadgets.isEmpty());

        player.addItemResult = true;
        animal.onInteract(player, request);
        animal.onInteract(player, request);
        assertEquals(2, player.itemsAdded);
        assertEquals(List.of(GADGET), player.interactedGadgets);
        assertEquals(1, scene.kills);
    }

    @Test
    void commissionCountsAMonsterDeathOnceForTheHost() {
        var monster = animal();
        monster.setGroupId(5);

        monster.onDeath(0);
        monster.onDeath(0);

        assertEquals(1, player.commissionKills);
    }

    private EntityMonster animal() {
        var data = decode(MonsterData.class, "{id:%d,type:'MONSTER_ENV_ANIMAL'}", ANIMAL);
        return new EntityMonster(scene, data, new Position(), new Position(), 1);
    }

    private static <T> T decode(Class<T> type, String json, Object... args) {
        return JsonUtils.decode(json.formatted(args), type);
    }
}
