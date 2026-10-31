package emu.grasscutter.game.drop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.DropItemData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.reward.RewardConfiguration;
import emu.grasscutter.scripts.data.SceneMonster;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;

public final class DropSystem extends BaseGameSystem {
    private final Int2ObjectMap<DropTableData> dropTable;
    private final Map<String, List<BaseDropData>> chestReward;
    private final Map<String, List<BaseDropData>> monsterDrop;
    private final Random rand;

    // TODO: don't know how to determine boss level.Have to hard-code the data from wiki.
    private final int[] bossLevel = {36, 37, 41, 50, 62, 72, 83, 91, 93, 103};

    public DropSystem(GameServer server) {
        super(server);

        this.rand = new Random();
        this.dropTable = GameData.getDropTableDataMap();
        this.chestReward = new HashMap<>();
        this.monsterDrop = new HashMap<>();

        try {
            var dataList = DataLoader.loadList("ChestDrop.json", ChestDropData.class);
            for (var i : dataList) {
                if (!chestReward.containsKey(i.getIndex())) {
                    chestReward.put(i.getIndex(), new ArrayList<>());
                }
                chestReward.get(i.getIndex()).add(i);
            }
        } catch (Exception ignored) {
            Grasscutter.getLogger()
                    .error("Unable to load chest drop data. Please place ChestDrop.json in data folder.");
        }

        try {
            var dataList = DataLoader.loadList("MonsterDrop.json", BaseDropData.class);
            for (var i : dataList) {
                if (!monsterDrop.containsKey(i.getIndex())) {
                    monsterDrop.put(i.getIndex(), new ArrayList<>());
                }
                monsterDrop.get(i.getIndex()).add(i);
            }
        } catch (Exception ignored) {
            Grasscutter.getLogger()
                    .error("Unable to load monster drop data. Please place MonsterDrop.json in data folder.");
        }
    }

    private int queryDropData(String dropTag, int level, Map<String, List<BaseDropData>> rewards) {
        if (!rewards.containsKey(dropTag)) return 0;

        var rewardList = rewards.get(dropTag);
        BaseDropData dropData = null;
        int minLevel = 0;
        for (var i : rewardList) {
            if (level >= i.getMinLevel() && i.getMinLevel() > minLevel) {
                minLevel = i.getMinLevel();
                dropData = i;
            }
        }
        if (dropData == null) return 0;
        return dropData.getDropId();
    }

    /**
     * Weekly Boss sources live in native ChestDrop (sourceType 35), not in the
     * DungeonExcel statueDrop column. Return candidate native roots for the
     * dungeon enemy level; the caller must independently verify boss identity.
     */
    public Map<String, Integer> weeklyBossRewardRoots(int level) {
        if (level <= 0) return Map.of();
        var roots = new java.util.TreeMap<String, Integer>();
        chestReward.forEach((tag, entries) -> {
            if (entries.stream().noneMatch(entry ->
                    entry instanceof ChestDropData chest && chest.getSourceType() == 35)) {
                return;
            }
            int root = queryDropData(tag, level, chestReward);
            if (hasUsableDungeonRewardRoot(root)) roots.put(tag, root);
        });
        return Map.copyOf(roots);
    }

    /** Use native roots only when they contain at least one positive-weight entry. */
    public boolean hasUsableDungeonRewardRoot(int dropId) {
        return dropId > 0 && dropTable.containsKey(dropId)
                && !isEmptyDropTable(dropTable.get(dropId));
    }

    public List<GameItem> handleDungeonRewardDrop(int dropId, boolean doubleReward) {
        if (!dropTable.containsKey(dropId)) return List.of();
        var dropData = dropTable.get(dropId);
        List<GameItem> items = new ArrayList<>();
        processDrop(dropData, doubleReward ? 2 : 1, items);
        return items;
    }

    /** TrainingPads / intentional empty killDrop — no materials, no legacy fallback. */
    private static final int EMPTY_KILL_DROP_ID = 99900001;

    private static boolean shouldSuppressMonsterDrops(EntityMonster monster) {
        if (monster == null) {
            return false;
        }
        String cn = monster.getClass().getName();
        if (cn != null && cn.startsWith("com.local.trainingpads.")) {
            return true;
        }
        try {
            if (monster.getMonsterData() != null
                    && monster.getMonsterData().getKillDropId() == EMPTY_KILL_DROP_ID) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** True when the drop table exists but would never yield items (empty / zero-weight vec). */
    private static boolean isEmptyDropTable(DropTableData dropData) {
        if (dropData == null) {
            return true;
        }
        var dropVec = dropData.getDropVec();
        if (dropVec == null || dropVec.isEmpty()) {
            return true;
        }
        for (var entry : dropVec) {
            if (entry != null && entry.getId() != 0 && entry.getWeight() > 0) {
                return false;
            }
        }
        return true;
    }

    public boolean handleMonsterDrop(EntityMonster monster) {
        // Training pad spawns set killDropId=99900001; without this they still get
        // InvestigationMonsterDropHelper materials, and missing drop-table entries
        // fall back to DropSystemLegacy (more materials).
        if (shouldSuppressMonsterDrops(monster)) {
            return true;
        }

        int killDropId = 0;
        try {
            if (monster.getMonsterData() != null) {
                killDropId = monster.getMonsterData().getKillDropId();
            }
        } catch (Throwable ignored) {
        }

        int dropId;
        int level = monster.getLevel();
        SceneMonster sceneMonster = monster.getMetaMonster();

        // Wildlife (fox/boar/crane/pigeon…): prefer MonsterExcel.killDropId quantities
        // (e.g. boar to raw meat, crane to fowl). Lua drop_tag used to map to an empty stub
        // DropTable rows — do not let those override a usable killDrop.
        boolean envAnimal = false;
        try {
            envAnimal =
                    monster.getMonsterData() != null
                            && monster.getMonsterData().getType()
                                    == emu.grasscutter.game.props.MonsterType.MONSTER_ENV_ANIMAL;
        } catch (Throwable ignored) {
        }
        boolean killDropUsable =
                killDropId != 0
                        && killDropId != EMPTY_KILL_DROP_ID
                        && dropTable.containsKey(killDropId)
                        && !isEmptyDropTable(dropTable.get(killDropId));

        if (envAnimal && killDropUsable) {
            dropId = killDropId;
        } else if (sceneMonster != null) {
            if (sceneMonster.drop_tag != null) {
                dropId = queryDropData(sceneMonster.drop_tag, level, monsterDrop);
            } else {
                dropId = sceneMonster.drop_id;
            }
        } else {
            dropId = killDropId;
        }

        if (dropId == EMPTY_KILL_DROP_ID) {
            return true;
        }

        // Empty/missing script table → killDrop, then EnvAnimalGather (crabs/lizards…).
        if (dropId == 0 || !dropTable.containsKey(dropId) || isEmptyDropTable(dropTable.get(dropId))) {
            if (killDropUsable) {
                dropId = killDropId;
            } else {
                boolean gathered = applyEnvAnimalGatherDrop(monster);
                try {
                    InvestigationMonsterDropHelper.tryDrop(monster);
                } catch (Throwable ignored) {
                }
                return gathered;
            }
        }

        boolean handled = applyMonsterDropTable(dropId, monster);

        // Still nothing (e.g. energy-only killDrop with empty process result): gather fallback.
        if (!handled) {
            handled = applyEnvAnimalGatherDrop(monster);
        }

        // Official killDrop / script drop_id are often energy-only; grant bounty materials.
        try {
            InvestigationMonsterDropHelper.tryDrop(monster);
        } catch (Throwable ignored) {
        }
        return handled;
    }

    /**
     * Apply a DropTableExcel row. Empty stub tables (null/empty/zero-weight dropVec) return false so
     * callers can fall back to killDropId / EnvAnimal gather / legacy Drop.json.
     */
    private boolean applyMonsterDropTable(int dropId, EntityMonster monster) {
        if (dropId <= 0 || !dropTable.containsKey(dropId)) {
            return false;
        }
        var dropData = dropTable.get(dropId);
        if (isEmptyDropTable(dropData)) {
            return false;
        }
        List<GameItem> items = new ArrayList<>();
        processDrop(dropData, 1, items);
        if (items.isEmpty()) {
            return false;
        }
        if (dropData.isFallToGround()) {
            List<Player> players = monster.getScene().getPlayers();
            if (!players.isEmpty()) {
                dropItems(items, ActionReason.MonsterDie, monster, players.get(0), true);
            }
        } else {
            for (Player p : monster.getScene().getPlayers()) {
                p.getInventory().addItems(items, ActionReason.MonsterDie);
            }
        }
        return true;
    }

    /** Ground-drop gather items when killing env animals that have no killDrop table. */
    private boolean applyEnvAnimalGatherDrop(EntityMonster monster) {
        // Interact path already granted gather items before killEntity.
        if (monster.isEnvGatherRewarded()) {
            return true;
        }
        if (monster.getMonsterData() == null) {
            return false;
        }
        var gatherData =
                GameData.getEnvAnimalGatherConfigDataMap().get(monster.getMonsterData().getId());
        if (gatherData == null) {
            return false;
        }
        List<GameItem> items = new ArrayList<>();
        var list = gatherData.getGatherItemList();
        if (list != null) {
            for (var param : list) {
                if (param == null || param.getId() <= 0 || param.getCount() <= 0) {
                    continue;
                }
                items.add(new GameItem(param.getId(), param.getCount()));
            }
        } else if (gatherData.getGatherItem() != null
                && gatherData.getGatherItem().getId() > 0
                && gatherData.getGatherItem().getCount() > 0) {
            items.add(
                    new GameItem(
                            gatherData.getGatherItem().getId(), gatherData.getGatherItem().getCount()));
        }
        if (items.isEmpty()) {
            return false;
        }
        List<Player> players = monster.getScene().getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        dropItems(items, ActionReason.MonsterEnvAnimal, monster, players.get(0), true);
        return true;
    }

    public boolean hasQuestItemDrop(int dropId) {
        return hasQuestItemDrop(dropId, new HashSet<>());
    }

    private boolean hasQuestItemDrop(int dropId, Set<Integer> visited) {
        if (!visited.add(dropId)) return false;
        var table = dropTable.get(dropId);
        if (table == null || table.getDropVec() == null) return false;
        for (var entry : table.getDropVec()) {
            if (entry == null || entry.getWeight() <= 0) continue;
            int id = entry.getId();
            if (dropTable.containsKey(id)) {
                if (hasQuestItemDrop(id, visited)) return true;
            } else {
                var item = GameData.getItemDataMap().get(id);
                if (item != null && item.getMaterialType() == MaterialType.MATERIAL_QUEST) return true;
            }
        }
        return false;
    }

    /** Native rewards for non-chest interactables, without delivering before claim admission. */
    public record NativeInteractDrop(List<GameItem> items, boolean fallToGround) {}

    public NativeInteractDrop rollNativeInteractDrop(emu.grasscutter.scripts.data.SceneGadget meta) {
        if (meta == null) throw new IllegalArgumentException("Missing native interact metadata");
        int dropId = meta.chest_drop_id > 0 ? meta.chest_drop_id
                : queryDropData(meta.drop_tag, Math.max(1, meta.level), chestReward);
        var table = dropTable.get(dropId);
        if (table == null) throw new IllegalStateException("Missing native interact drop: " + dropId);
        var items = new ArrayList<GameItem>();
        processDrop(table, Math.max(1, meta.drop_count), items);
        return new NativeInteractDrop(List.copyOf(items), table.isFallToGround());
    }

    public boolean handleChestDrop(int chestDropId, int dropCount, GameEntity bornFrom) {
        if (!dropTable.containsKey(chestDropId)) return false;
        var dropData = dropTable.get(chestDropId);
        List<GameItem> items = new ArrayList<>();
        processDrop(dropData, dropCount, items);
        if (items.isEmpty()) {
            Grasscutter.getLogger()
                    .info("Chest drop {} rolled nothing (native table has {} entries)",
                            chestDropId, dropData.getDropVec().size());
        }
        if (dropData.isFallToGround()) {
            dropItems(items, ActionReason.OpenChest, bornFrom, bornFrom.getWorld().getHost(), false);
        } else {
            bornFrom.getWorld().getHost().getInventory().addItems(items, ActionReason.OpenChest);
        }
        return true;
    }

    public boolean handleChestDrop(String dropTag, int level, GameEntity bornFrom) {
        int dropId = queryDropData(dropTag, level, chestReward);
        if (dropId == 0) return false;
        return handleChestDrop(dropId, 1, bornFrom);
    }

    /**
     * Return native boss drops without granting, rewriting artifacts, or using preview data.
     * The caller authorizes resin and inventory as one admission operation.
     */
    public List<GameItem> rollBossChestDrop(String dropTag, int worldLevel) {
        if (dropTag == null || dropTag.isBlank()
                || worldLevel < 0 || worldLevel >= bossLevel.length) {
            throw new IllegalArgumentException(
                    "Unidentified boss tag/world level: " + dropTag + "/" + worldLevel);
        }
        var configured = RewardConfiguration.current().rollWorldBoss(
                dropTag, bossLevel[worldLevel], rand);
        if (configured != null) {
            List<GameItem> items = new ArrayList<>();
            configured.forEach((id, amount) -> {
                if (amount > 0) appendNativeDrop(id, amount, items);
            });
            if (items.isEmpty()) {
                throw new IllegalStateException(
                        "Empty configured world boss rewards: " + dropTag
                                + " WL" + worldLevel);
            }
            return items;
        }
        int dropId = queryDropData(dropTag, bossLevel[worldLevel], chestReward);
        if (dropId <= 0 || !dropTable.containsKey(dropId)) {
            throw new IllegalStateException(
                    "Missing native boss drop table: " + dropTag
                            + " WL" + worldLevel + " root=" + dropId);
        }
        var dropData = dropTable.get(dropId);
        if (isEmptyDropTable(dropData)) {
            throw new IllegalStateException(
                    "Empty native boss drop table: " + dropTag + " WL" + worldLevel);
        }
        List<GameItem> items = new ArrayList<>();
        processDrop(dropData, 1, items);
        if (items.isEmpty()) {
            throw new IllegalStateException(
                    "Native boss drop rolled no rewards: " + dropTag + " WL" + worldLevel);
        }
        return items;
    }

    /**
     * Equipment constructors represent one instance, even when passed count > 1.
     * Expand each roll before constructing it, rather than merging distinct equipment.
     */
    static void appendNativeDrop(int id, int amount, List<GameItem> items) {
        var data = GameData.getItemDataMap().get(id);
        if (data == null) {
            throw new IllegalStateException("Native drop references unknown item: " + id);
        }
        if (data.getItemType() == ItemType.ITEM_WEAPON
                || data.getItemType() == ItemType.ITEM_RELIQUARY) {
            for (int i = 0; i < amount; i++) items.add(new GameItem(data, 1));
            return;
        }
        for (GameItem item : items) {
            if (item.getItemId() == id) {
                item.setCount(Math.addExact(item.getCount(), amount));
                return;
            }
        }
        items.add(new GameItem(data, amount));
    }

    private void processDrop(DropTableData dropData, int count, List<GameItem> items) {
        // TODO:Not clear on the meaning of some fields,like "dropLevel".Will ignore them.
        // TODO:solve drop limits,like everydayLimit.
        if (count > 1) {
            for (int i = 0; i < count; i++) processDrop(dropData, 1, items);
            return;
        }
        if (dropData.getRandomType() == 0) {
            int weightSum = 0;
            for (var i : dropData.getDropVec()) {
                int id = i.getId();
                if (id == 0) continue;
                weightSum += i.getWeight();
            }
            if (weightSum == 0) return;
            int weight = rand.nextInt(weightSum);
            int sum = 0;
            for (var i : dropData.getDropVec()) {
                int id = i.getId();
                if (id == 0) continue;
                sum += i.getWeight();
                if (weight < sum) {
                    // win the item
                    int amount = calculateDropAmount(i) * count;
                    if (amount <= 0) break;
                    if (dropTable.containsKey(id)) {
                        processDrop(dropTable.get(id), amount, items);
                    } else {
                        appendNativeDrop(id, amount, items);
                    }
                    break;
                }
            }
        } else if (dropData.getRandomType() == 1) {
            for (var i : dropData.getDropVec()) {
                int id = i.getId();
                if (id == 0) continue;
                if (rand.nextInt(10000) < i.getWeight()) {
                    int amount = calculateDropAmount(i) * count;
                    if (amount <= 0) continue;
                    if (dropTable.containsKey(id)) {
                        processDrop(dropTable.get(id), amount, items);
                    } else {
                        appendNativeDrop(id, amount, items);
                    }
                }
            }
        }
    }

    private int calculateDropAmount(DropItemData i) {
        int amount;
        if (i.getCountRange().contains(";")) {
            String[] ranges = i.getCountRange().split(";");
            amount = rand.nextInt(Integer.parseInt(ranges[0]), Integer.parseInt(ranges[1]) + 1);
        } else if (i.getCountRange().contains(".")) {
            double expectAmount = Double.parseDouble(i.getCountRange());
            amount = (int) expectAmount;
            if (rand.nextDouble() < expectAmount - amount) amount++;
        } else {
            amount = Integer.parseInt(i.getCountRange());
        }
        return amount;
    }

    /**
     * @param share Whether other players in the scene could see the drop items.
     */
    private void dropItem(
            GameItem item, ActionReason reason, Player player, GameEntity bornFrom, boolean share) {
        DropMaterialData drop = GameData.getDropMaterialDataMap().get(item.getItemId());
        if ((drop != null && drop.isAutoPick())
                || (item.getItemData().getItemType() == ItemType.ITEM_VIRTUAL
                        && item.getItemData().getGadgetId() == 0)) {
            giveItem(item, reason, player, share);
        } else {
            // TODO:solve share problem
            player.getScene().addDropEntity(item, bornFrom, player, share);
        }
    }

    private void dropItems(
            List<GameItem> items,
            ActionReason reason,
            GameEntity bornFrom,
            Player player,
            boolean share) {
        for (var i : items) {
            dropItem(i, reason, player, bornFrom, share);
        }
    }

    private void giveItem(GameItem item, ActionReason reason, Player player, boolean share) {
        if (share) {
            for (var p : player.getScene().getPlayers()) {
                p.getInventory().addItem(item, reason);
                p.sendPacket(new PacketDropHintNotify(item.getItemId(), player.getPosition().toProto()));
            }
        } else {
            player.getInventory().addItem(item, reason);
            player.sendPacket(new PacketDropHintNotify(item.getItemId(), player.getPosition().toProto()));
        }
    }

    private void giveItems(List<GameItem> items, ActionReason reason, Player player, boolean share) {
        // don't know whether we need PacketDropHintNotify.
        if (share) {
            for (var p : player.getScene().getPlayers()) {
                p.getInventory().addItems(items, reason);
                p.sendPacket(new PacketDropHintNotify(items, player.getPosition().toProto()));
            }
        } else {
            player.getInventory().addItems(items, reason);
            player.sendPacket(new PacketDropHintNotify(items, player.getPosition().toProto()));
        }
    }
}
