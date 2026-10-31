package emu.grasscutter.game.managers.energy;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.actions.ActionGenerateElemBall;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.AbilityActionGenerateElemBallOuterClass.AbilityActionGenerateElemBall;
import emu.grasscutter.net.proto.AbilityInvokeArgumentOuterClass.AbilityInvokeArgument;
import emu.grasscutter.net.proto.AbilityInvokeEntryOuterClass.AbilityInvokeEntry;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import sun.misc.Unsafe;

/** Actual particle creation paths with a recording scene; no server or player inventory. */
@ExtendWith(ServerResourceFixture.class)
final class EnergyParticleGenerationTest {
    private final Gson gson = new Gson();
    private RecordingScene scene;
    private RecordingPlayer player;
    private final Map<Integer,ItemData> previous = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        scene = allocate(RecordingScene.class);
        scene.drops = new ArrayList<>();
        set(scene, Scene.class, "sceneData", gson.fromJson("{\"id\":3,\"sceneType\":\"SCENE_WORLD\"}", SceneData.class));
        set(scene, Scene.class, "world", allocate(RecordingWorld.class));
        player = allocate(RecordingPlayer.class);
        set(player, Player.class, "scene", scene);
        set(player, Player.class, "teamManager", new TeamManager());
        set(player, Player.class, "energyManager", new EnergyManager(player));
        for (int id : new int[]{2017,2021,2022,2024}) {
            previous.put(id, GameData.getItemDataMap().put(id,gson.fromJson(
                    "{\"id\":"+id+",\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_ADSORBATE\","
                    +"\"gadgetId\":70610006,\"itemUse\":[{\"useOp\":\"ITEM_USE_ADD_ELEM_ENERGY\",\"useParam\":[\"5\",\"3\",\"1\"]}]}",ItemData.class)));
        }
    }

    @AfterEach
    void restore() {
        previous.forEach((id,value) -> {
            if (value == null) GameData.getItemDataMap().remove((int)id);
            else GameData.getItemDataMap().put((int)id,value);
        });
    }

    private AbilityActionGenerateElemBall payload() {
        return AbilityActionGenerateElemBall.newBuilder().setPos(Vector.newBuilder().setX(10).setY(20).setZ(30)).build();
    }

    @Test
    void clientParticleInvokeCreatesVisibleEnergyDrops() throws Exception {
        var invoke = AbilityInvokeEntry.newBuilder().setEntityId(123)
                .setArgumentType(AbilityInvokeArgument.AbilityInvokeArgument_ABILITY_ACTION_GENERATE_ELEM_BALL)
                .setAbilityData(payload().toByteString()).build();
        new AbilityManager(player).onAbilityInvoke(invoke);
        assertEquals(2,scene.drops.size(),"An incoming particle action must create scene entities");
        for (var drop : scene.drops) {
            assertEquals(2024,drop.getItemData().getId());
            assertEquals(10f,drop.getPosition().getX());
            assertEquals(20f,drop.getPosition().getY());
            assertEquals(30f,drop.getPosition().getZ());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"2017,15,5", "2021,9,3", "2021,12,4", "2022,3,1"})
    void skillParticleActionsCreateExpectedElementAndCount(int itemId, int energy, int count) throws Exception {
        // Numeric actions from installed Arlecchino, Kazuha (tap/hold), and Kaeya resources.
        var action = emu.grasscutter.utils.JsonUtils.loadToClass(new java.io.StringReader(
                "{\"$type\":\"GenerateElemBall\",\"configID\":"+itemId+",\"baseEnergy\":"+energy+",\"ratio\":1}"),
                AbilityModifierAction.class);
        var owner = allocate(EntityAvatar.class);
        var avatar = allocate(emu.grasscutter.game.avatar.Avatar.class);
        set(avatar,emu.grasscutter.game.avatar.Avatar.class,"owner",player);
        set(owner,EntityAvatar.class,"avatar",avatar);
        set(owner,GameEntity.class,"scene",scene);
        var ability = allocate(Ability.class);
        set(ability,Ability.class,"owner",owner);
        var bytes = payload().toByteString();
        if (itemId == 2017 || itemId == 2021) {
            var hex = itemId == 2017
                    ? "5a0f0dbf3224451588d97d431d9a1ad2c4720515ae299943"
                    : "5a0f0d5f3f2445152a6580431d451ad2c4720515e21b8842";
            bytes = com.google.protobuf.ByteString.copyFrom(java.util.HexFormat.of().parseHex(hex));
        }
        assertTrue(new ActionGenerateElemBall().execute(ability,action,bytes,owner));
        assertEquals(count,scene.drops.size(),"A skill action must spawn its configured energy particles");
        assertEquals(itemId,scene.drops.getFirst().getItemData().getId());
        if (itemId == 2017 || itemId == 2021) {
            for (var drop : scene.drops) {
                assertEquals(itemId == 2017 ? 2627.1716f : 2627.9607f, drop.getPosition().getX(), 0.001f);
                assertEquals(itemId == 2017 ? 253.84973f : 256.79034f, drop.getPosition().getY(), 0.001f);
                assertEquals(itemId == 2017 ? -1680.8313f : -1680.8209f, drop.getPosition().getZ(), 0.001f);
            }
        }
        assertEquals(1,scene.drops.getFirst().getCount());
    }

    private static void set(Object owner,Class<?> type,String name,Object value) throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        return type.cast(((Unsafe)field.get(null)).allocateInstance(type));
    }
    private static final class RecordingScene extends Scene {
        List<EntityItem> drops;
        private RecordingScene() { super(null,null); }
        @Override public GameEntity getEntityById(int id) { return null; }
        @Override public void addEntity(GameEntity entity) { drops.add((EntityItem)entity); }
    }
    private static final class RecordingWorld extends World {
        int nextId;
        private RecordingWorld() { super((Player)null); }
        @Override public int getNextEntityId(EntityIdType type) { return ++nextId; }
    }
    private static final class RecordingPlayer extends Player {
        @Override public long getNextGameGuid() { return 1; }
    }
}
