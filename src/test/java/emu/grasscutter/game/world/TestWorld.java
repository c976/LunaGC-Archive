package emu.grasscutter.game.world;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.dailytask.DailyTaskManager;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.props.SceneType;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.utils.JsonUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import sun.misc.Unsafe;

/** A world without a game server, and the scene and player fakes the world-quest tests share. */
public class TestWorld extends World {
    private Player testHost;
    private List<Player> testPlayers;
    private int nextEntityId;
    /** Transfers seen while {@link #transferResult} is set; null runs the real transfer. */
    public List<TeleportProperties> transfers;
    public Boolean transferResult;

    private TestWorld() {
        super((Player) null); // Never runs: World's constructors need a running GameServer.
    }

    public static TestWorld create(Player host) {
        GameData.setConfigGlobalCombat(new ConfigGlobalCombat());
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var world = (TestWorld) ((Unsafe) field.get(null)).allocateInstance(TestWorld.class);
            world.testHost = host;
            world.testPlayers = new ArrayList<>(List.of(host));
            world.transfers = new ArrayList<>();
            host.setWorld(world);
            return world;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public Player getHost() {
        return testHost;
    }

    @Override
    public List<Player> getPlayers() {
        return testPlayers;
    }

    @Override
    public int getLevelEntityId() {
        return 0;
    }

    @Override
    public int getNextEntityId(EntityIdType idType) {
        return (idType.getId() << 24) + ++nextEntityId;
    }

    @Override
    public boolean transferPlayerToScene(Player player, TeleportProperties properties) {
        if (transferResult == null) return super.transferPlayerToScene(player, properties);
        transfers.add(properties);
        return transferResult;
    }

    public static SceneData sceneData(int id, SceneType type) {
        return JsonUtils.decode("{\"id\":" + id + ",\"type\":\"" + type + "\"}", SceneData.class);
    }

    /** Counts saves and kills instead of reaching the database and the death pipeline. */
    public static class TestScene extends Scene {
        public int saves;
        public int cachedGroupsAtSave = -1;
        public int kills;

        public TestScene(World world, int id, SceneType type) {
            super(world, sceneData(id, type));
        }

        @Override
        public void saveGroups() {
            saves++;
            cachedGroupsAtSave = getScriptManager().getCachedGroupInstances().size();
        }

        @Override
        public void killEntity(GameEntity target, int attackerId) {
            kills++;
        }
    }

    /** Records interaction credit and commission kills. */
    public static class TestPlayer extends Player {
        public final List<Integer> interactedGadgets = new ArrayList<>();
        public int commissionKills;
        public int itemsAdded;
        public boolean addItemResult = true;
        public int level = 60;
        private final Inventory inventory =
                new Inventory(this) {
                    @Override
                    public boolean addItem(ItemParamData item, ActionReason reason) {
                        itemsAdded++;
                        return addItemResult;
                    }
                };
        private final DailyTaskManager dailyTasks =
                new DailyTaskManager() {
                    @Override
                    public synchronized void onMonsterDeath(Scene scene, int groupId, Position deathPos) {
                        commissionKills++;
                    }
                };

        @Override
        public void onGadgetInteracted(int gadgetId) {
            interactedGadgets.add(gadgetId);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        @Override
        public void sendPacket(BasePacket packet) {}

        @Override
        public DailyTaskManager getDailyTaskManager() {
            return dailyTasks;
        }

        @Override
        public int getLevel() {
            return level;
        }
    }
}
