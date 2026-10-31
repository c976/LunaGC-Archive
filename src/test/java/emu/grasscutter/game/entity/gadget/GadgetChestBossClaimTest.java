package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.systems.TestPlayers;
import emu.grasscutter.game.systems.TestPlayers.TestPlayer;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.World;
import emu.grasscutter.net.proto.BossChestInfoOuterClass.BossChestInfo;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.scripts.data.SceneBossChest;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.utils.JsonUtils;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ServerResourceFixture.class)
final class GadgetChestBossClaimTest {
    private static final int OPENED = 102;
    private static final int FLOWER_ID = 99000001;
    private TestPlayer a, b;
    private Scene scene;
    private EntityGadget flower;
    private TestChest chest;
    private boolean oldScriptMode;

    @BeforeEach
    void setup() {
        oldScriptMode = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        var server = TestPlayers.server();
        a = new TestPlayer(server, 100);
        b = new TestPlayer(server, 200);
        scene = new Scene(new World(a), JsonUtils.decode("{\"id\": 3}", SceneData.class));
        scene.getPlayers().addAll(List.of(a, b));
        GameData.getGadgetDataMap()
                .put(FLOWER_ID, JsonUtils.decode("{\"id\": " + FLOWER_ID + "}", GadgetData.class));
        // The flower stands where the players are; only nearby players qualify.
        flower = new EntityGadget(scene, FLOWER_ID, new Position(a.getPosition()));
        var meta = new SceneGadget();
        meta.boss_chest = new SceneBossChest();
        meta.boss_chest.resin = 40;
        flower.setMetaGadget(meta);
        chest = new TestChest(flower);
        // Off while building the entity (avoids script loading); on for onInteract's boss path.
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = true;
        info(); // the flower appears to everyone in the scene
    }

    @AfterEach
    void restore() {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = oldScriptMode;
        GameData.getGadgetDataMap().remove(FLOWER_ID);
    }

    @Test
    void genericGadgetWithBossChestMetadataBecomesClaimableFlower() {
        var generic = new EntityGadget(scene, FLOWER_ID, new Position(a.getPosition()));
        var meta = new SceneGadget();
        meta.boss_chest = new SceneBossChest();
        meta.boss_chest.resin = 40;
        meta.boss_chest.monster_config_id = 543001;
        generic.setMetaGadget(meta);

        // World boss flowers can use generic GadgetData, with their real function
        // supplied by the scene script's boss_chest field, even for generic gadgets.
        generic.buildContent();
        assertInstanceOf(GadgetChest.class, generic.getContent());

        var builder = SceneGadgetInfo.newBuilder();
        generic.getContent().onBuildProto(builder);
        assertTrue(builder.hasBossChest());
        assertEquals(40, builder.getBossChest().getResin());
        assertTrue(builder.getBossChest().getRemainUidListList().contains(a.getUid()));
    }

    @Test
    void eachEligiblePlayerClaimsOnceAndTheFlowerOpensAfterTheLast() {
        claim(a);
        claim(a);
        assertEquals(Map.of(100, 1), chest.grants);
        assertNotEquals(OPENED, flower.getState());
        assertEquals(List.of(100, 200), info().getQualifyUidListList());
        assertEquals(List.of(200), info().getRemainUidListList());

        claim(b);
        assertEquals(Map.of(100, 1, 200, 1), chest.grants);
        assertEquals(OPENED, flower.getState());
    }

    @Test
    void worldBossClaimDoesNotDependOnBigWorldScriptMode() {
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        claim(a);
        assertEquals(Map.of(100, 1), chest.grants);
    }

    @Test
    void failedBossRollDoesNotMarkTheFlowerClaimed() {
        chest.allowGrant = false;
        claim(a);
        assertTrue(chest.grants.isEmpty());
        assertNotEquals(OPENED, flower.getState());
        chest.allowGrant = true;
        claim(a);
        assertEquals(Map.of(100, 1), chest.grants);
    }

    @Test
    void playersFarFromTheFlowerDoNotQualify() {
        var far = new TestPlayer(TestPlayers.server(), 300);
        far.getPosition().set(new Position(a.getPosition().getX() + 500, a.getPosition().getY(), a.getPosition().getZ()));
        scene.getPlayers().add(far);
        var other = new EntityGadget(scene, FLOWER_ID, new Position(a.getPosition()));
        other.setMetaGadget(flower.getMetaGadget());
        var otherChest = new TestChest(other);

        var builder = SceneGadgetInfo.newBuilder();
        otherChest.onBuildProto(builder);

        assertEquals(List.of(100, 200), builder.getBossChest().getQualifyUidListList());
    }

    @Test
    void bossChestMetadataOverridesGenericGadgetType() {
        var generated = new EntityGadget(scene, FLOWER_ID, new Position(a.getPosition()));
        generated.setMetaGadget(flower.getMetaGadget());
        generated.buildContent();

        assertInstanceOf(GadgetChest.class, generated.getContent());
    }

    @Test
    void anInitiallyUnattendedFlowerCanBeClaimedAfterApproach() {
        var position = new Position(flower.getPosition());
        a.getPosition().set(new Position(position.getX() + 500, position.getY(), position.getZ()));
        b.getPosition().set(new Position(position.getX() + 500, position.getY(), position.getZ()));

        var fresh = new EntityGadget(scene, FLOWER_ID, position);
        fresh.setMetaGadget(flower.getMetaGadget());
        var freshChest = new TestChest(fresh);
        var builder = SceneGadgetInfo.newBuilder();
        freshChest.onBuildProto(builder);
        assertTrue(builder.getBossChest().getQualifyUidListList().isEmpty());

        a.getPosition().set(position);
        freshChest.onInteract(
                a,
                GadgetInteractReq.newBuilder()
                        .setOpType(InterOpType.InterOpType_INTER_OP_FINISH)
                        .build());

        assertEquals(Map.of(100, 1), freshChest.grants);
        assertEquals(OPENED, fresh.getState());
    }

    @Test
    void startInteractionOnlyOpensTheClaimDialog() {
        chest.onInteract(
                a,
                GadgetInteractReq.newBuilder()
                        .setOpType(InterOpType.InterOpType_INTER_OP_START)
                        .build());

        assertTrue(chest.grants.isEmpty());
        assertNotEquals(OPENED, flower.getState());
        claim(a);
        assertEquals(Map.of(100, 1), chest.grants);
    }

    @Test
    void lateArrivalCannotClaim() {
        var late = new TestPlayer(TestPlayers.server(), 300);
        scene.getPlayers().add(late);

        claim(late);

        assertTrue(chest.grants.isEmpty());
        assertEquals(List.of(100, 200), info().getQualifyUidListList());
    }

    @Test
    void eligiblePlayerWhoLeftDoesNotKeepTheFlowerOpen() {
        scene.getPlayers().remove(b);

        claim(a);

        assertEquals(OPENED, flower.getState());
    }

    @Test
    void claimedPlayerFinishesTheFlowerAfterTheOthersLeave() {
        claim(a);
        assertNotEquals(OPENED, flower.getState());

        scene.getPlayers().remove(b);
        assertEquals(1, a.getPlayerProgress().getHandbookChestOpenCount());
        claim(a);

        assertEquals(OPENED, flower.getState());
        assertEquals(Map.of(100, 1), chest.grants);
        assertEquals(1, a.getPlayerProgress().getHandbookChestOpenCount());
    }

    private BossChestInfo info() {
        var builder = SceneGadgetInfo.newBuilder();
        chest.onBuildProto(builder);
        return builder.getBossChest();
    }

    private void claim(Player player) {
        chest.onInteract(
                player,
                GadgetInteractReq.newBuilder().setOpType(InterOpType.InterOpType_INTER_OP_FINISH).build());
    }

    private static final class TestChest extends GadgetChest {
        final Map<Integer, Integer> grants = new HashMap<>();
        boolean allowGrant = true;

        TestChest(EntityGadget gadget) {
            super(gadget);
        }

        @Override
        protected boolean grantWorldBossReward(Player player, SceneGadget meta, int resinCost) {
            if (!allowGrant) return false;
            grants.merge(player.getUid(), 1, Integer::sum);
            return true;
        }
    }
}
