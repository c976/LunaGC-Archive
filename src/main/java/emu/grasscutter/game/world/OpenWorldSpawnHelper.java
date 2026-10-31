/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mongodb.client.MongoCollection
 *  com.mongodb.client.model.Filters
 *  com.mongodb.client.model.ReplaceOptions
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.GameDepot
 *  emu.grasscutter.data.excels.GatherData
 *  emu.grasscutter.data.excels.RefreshPolicyExcelConfigData
 *  emu.grasscutter.database.DatabaseManager
 *  emu.grasscutter.game.entity.EntityGadget
 *  emu.grasscutter.game.entity.EntityMonster
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.world.NodKraiExploreSpawnHelper
 *  emu.grasscutter.game.world.SnezhnayaExploreSpawnHelper
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.game.world.SceneGroupInstance
 *  emu.grasscutter.game.world.SpawnDataEntry
 *  emu.grasscutter.game.world.SpawnDataEntry$GridBlockId
 *  emu.grasscutter.scripts.data.SceneGadget
 *  emu.grasscutter.scripts.data.SceneGroup
 *  org.bson.Document
 *  org.bson.conversions.Bson
 */
package emu.grasscutter.game.world;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.excels.GatherData;
import emu.grasscutter.data.excels.RefreshPolicyExcelConfigData;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.inventory.MaterialType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.NodKraiExploreSpawnHelper;
import emu.grasscutter.game.world.SnezhnayaExploreSpawnHelper;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.SceneGroupInstance;
import emu.grasscutter.game.world.SpawnDataEntry;
import emu.grasscutter.game.world.WorldBossSpawnHelper;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class OpenWorldSpawnHelper {
    private static final String COLLECTION = "open_world_spawns";
    public static final int DEFAULT_REFRESH_ID = 1000;
    public static final int GATHER_REFRESH_DAILY = 999900;
    public static final int GATHER_REFRESH_SPECIALTY = 999921;
    public static final int GATHER_REFRESH_CRYSTAL = 999932;
    private static final Map<String, Long> LOCAL_COOLDOWN = new ConcurrentHashMap<String, Long>();
    private static volatile Field loadedGridBlocksField;

    private OpenWorldSpawnHelper() {
    }

    private static String key(int n, int n2, int n3, int n4) {
        return n + ":" + n2 + ":" + n3 + ":" + n4;
    }

    private static MongoCollection<Document> col() {
        return DatabaseManager.getGameDatabase().getCollection(COLLECTION);
    }

    private static int resolveOwnerUid(Scene scene) {
        if (scene == null || scene.getWorld() == null) {
            return 0;
        }
        Player player = scene.getWorld().getHost();
        if (player != null) {
            return player.getUid();
        }
        List list = scene.getPlayers();
        if (list != null && !list.isEmpty() && list.get(0) != null) {
            return ((Player)list.get(0)).getUid();
        }
        return 0;
    }

    private static boolean isOpenWorldScene(Scene scene) {
        if (scene == null) {
            return false;
        }
        try {
            if (scene.getDungeonManager() != null) {
                return false;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        int n = scene.getId();
        return n > 0 && n < 2000;
    }

    private static int resolveRefreshId(Scene scene, int n) {
        try {
            if (scene != null && scene.getScriptManager() != null) {
                SceneGroupInstance sceneGroupInstance = scene.getScriptManager().getGroupInstanceById(n);
                if (sceneGroupInstance != null && sceneGroupInstance.getLuaGroup() != null && sceneGroupInstance.getLuaGroup().refresh_id > 0) {
                    return sceneGroupInstance.getLuaGroup().refresh_id;
                }
                // Cooldown bookkeeping must not activate a group after its final death unloaded it.
                // Cached instances and already-parsed block metadata are safe, non-loading sources.
                SceneGroupInstance cached = scene.getScriptManager().getCachedGroupInstances().get(n);
                if (cached != null && cached.getLuaGroup() != null && cached.getLuaGroup().refresh_id > 0) {
                    return cached.getLuaGroup().refresh_id;
                }
                for (var block : scene.getScriptManager().getBlocks().values()) {
                    SceneGroup group = block.groups == null ? null : block.groups.get(n);
                    if (group != null && group.refresh_id > 0) {
                        return group.refresh_id;
                    }
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 1000;
    }

    public static int resolveGatherRefreshId(EntityGadget entityGadget, int n) {
        try {
            if (entityGadget != null) {
                GatherData gatherData = (GatherData)GameData.getGatherDataMap().get(entityGadget.getPointType());
                if (gatherData != null && gatherData.getRefreshId() > 0) {
                    return OpenWorldSpawnHelper.normalizeGatherRefreshId(gatherData.getRefreshId());
                }
                int n2 = entityGadget.getGadgetId();
                for (GatherData gatherData2 : GameData.getGatherDataMap().values()) {
                    if (gatherData2 != null && gatherData2.getGadgetId() == n2 && gatherData2.getRefreshId() > 0) {
                        return OpenWorldSpawnHelper.normalizeGatherRefreshId(gatherData2.getRefreshId());
                    }
                    if (gatherData2 == null || n <= 0 || gatherData2.getItemId() != n || gatherData2.getRefreshId() <= 0) continue;
                    return OpenWorldSpawnHelper.normalizeGatherRefreshId(gatherData2.getRefreshId());
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return OpenWorldSpawnHelper.guessGatherRefreshIdByItem(n);
    }

    private static int normalizeGatherRefreshId(int n) {
        return switch (n) {
            case 999930 -> 999900;
            case 999920, 999931 -> 999921;
            case 999932 -> 999932;
            case 999900, 999921 -> n;
            default -> n > 0 ? n : 999900;
        };
    }

    private static int guessGatherRefreshIdByItem(int n) {
        if (n == 101003 || n == 101008 || n == 101004) {
            return 999932;
        }
        if (n == 101001) {
            return 999900;
        }
        if (n == 101002 || n == 101006 || n == 100052 || n == 100053 || n == 100054) {
            return 999921;
        }
        if (n >= 100001 && n <= 100020) {
            return 999900;
        }
        if (n == 100051 || n == 100062 || n == 100063) {
            return 999900;
        }
        if (n >= 100021 && n <= 100058 || n >= 101200 && n <= 101299 || n == 100028) {
            return 999921;
        }
        return 999900;
    }

    private static int intervalSeconds(Scene scene, int n) {
        if (n <= 0) {
            n = 1000;
        }
        try {
            int n2;
            RefreshPolicyExcelConfigData refreshPolicyExcelConfigData = (RefreshPolicyExcelConfigData)GameData.getRefreshPolicyExcelConfigDataMap().get(n);
            if (refreshPolicyExcelConfigData != null && scene != null && scene.getWorld() != null && (n2 = refreshPolicyExcelConfigData.getIntervalInSeconds(scene.getWorld())) > 0) {
                return n2;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return switch (n) {
            case 1, 990010, 999900, 999930 -> 86400;
            case 990000, 990020, 999920, 999921, 999931 -> 172800;
            case 990030, 999932 -> 259200;
            case 1003, 1100 -> 300;
            case 1004 -> 1800;
            case 99999 -> 60;
            case 1000, 1001 -> 43200;
            default -> 86400;
        };
    }

    private static long readConsumedAt(Document document) {
        if (document == null) {
            return 0L;
        }
        Object object = document.get((Object)"consumedAt");
        if (object instanceof Number) {
            Number number = (Number)object;
            return number.longValue();
        }
        return 0L;
    }

    private static int readRefreshId(Document document, Scene scene, int n) {
        Number number;
        Object object;
        if (document != null && (object = document.get((Object)"refreshId")) instanceof Number && (number = (Number)object).intValue() > 0) {
            return number.intValue();
        }
        return OpenWorldSpawnHelper.resolveRefreshId(scene, n);
    }

    /** Oculi (MATERIAL_ELEM_CRYSTAL) are collected once per world and never come back. */
    public static boolean isPermanentGather(int itemId) {
        var item = GameData.getItemDataMap().get(itemId);
        return item != null && item.getMaterialType() == MaterialType.MATERIAL_ELEM_CRYSTAL;
    }

    /** A Lua gather point whose gather item is an oculus. */
    public static boolean isPermanentGatherPoint(int pointType) {
        if (pointType <= 0) return false;
        GatherData gather = GameData.getGatherDataMap().get(pointType);
        return gather != null && isPermanentGather(gather.getItemId());
    }

    private static boolean isExpired(Scene scene, int n, Document document) {
        int n2;
        long l = OpenWorldSpawnHelper.readConsumedAt(document);
        if (l <= 0L) {
            return true;
        }
        if (document.get("gatherItemId") instanceof Number item && isPermanentGather(item.intValue())) {
            return false;
        }
        long l2 = System.currentTimeMillis() / 1000L;
        return l2 >= l + (long)OpenWorldSpawnHelper.intervalSeconds(scene, n2 = OpenWorldSpawnHelper.readRefreshId(document, scene, n));
    }

    public static void onGathered(EntityGadget entityGadget, int n) {
        try {
            if (entityGadget != null) {
                NodKraiExploreSpawnHelper.markClaimed((EntityGadget)entityGadget);
                try {
                    SnezhnayaExploreSpawnHelper.markClaimed((EntityGadget)entityGadget);
                } catch (Throwable ignored) {
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        if (entityGadget == null) {
            return;
        }
        Scene scene = entityGadget.getScene();
        if (!OpenWorldSpawnHelper.isOpenWorldScene(scene)) {
            return;
        }
        int n2 = OpenWorldSpawnHelper.resolveOwnerUid(scene);
        if (n2 <= 0) {
            return;
        }
        int n3 = entityGadget.getGroupId();
        int n4 = entityGadget.getConfigId();
        if ((n3 <= 0 || n4 <= 0) && entityGadget.getSpawnEntry() != null && entityGadget.getSpawnEntry().getGroup() != null) {
            n3 = entityGadget.getSpawnEntry().getGroup().getGroupId();
            n4 = entityGadget.getSpawnEntry().getConfigId();
        }
        if (n3 <= 0 || n4 <= 0) {
            return;
        }
        int n5 = OpenWorldSpawnHelper.resolveGatherRefreshId(entityGadget, n);
        long l = System.currentTimeMillis() / 1000L;
        String string = OpenWorldSpawnHelper.key(n2, scene.getId(), n3, n4);
        LOCAL_COOLDOWN.put(string, l);
        try {
            Document persistDoc = new Document("ownerUid", n2).append("sceneId", scene.getId()).append("groupId", n3).append("configId", n4).append("gadgetId", entityGadget.getGadgetId()).append("monsterId", 0).append("gatherItemId", n).append("posX", entityGadget.getPosition() != null ? entityGadget.getPosition().getX() : 0.0f).append("posY", entityGadget.getPosition() != null ? entityGadget.getPosition().getY() : 0.0f).append("posZ", entityGadget.getPosition() != null ? entityGadget.getPosition().getZ() : 0.0f).append("consumedAt", l).append("refreshId", n5);
            OpenWorldSpawnHelper.col().replaceOne(Filters.and(Filters.eq("ownerUid", n2), Filters.eq("sceneId", scene.getId()), Filters.eq("groupId", n3), Filters.eq("configId", n4)), persistDoc, new ReplaceOptions().upsert(true));
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("OpenWorldSpawnHelper persist gather failed uid={} group={} cfg={}: {}", new Object[]{n2, n3, n4, throwable.toString()});
        }
        try {
            if (scene.getScriptManager() != null) {
                SceneGroupInstance groupInstance = scene.getScriptManager().getGroupInstanceById(n3);
                if (groupInstance != null) {
                    groupInstance.getDeadEntities().add(n4);
                    groupInstance.save();
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        Grasscutter.getLogger().debug("GatherRefresh recorded item={} refreshId={} interval={}s group={} cfg={}", new Object[]{n, n5, OpenWorldSpawnHelper.intervalSeconds(scene, n5), n3, n4});
    }

    public static void onMonsterKilled(EntityMonster entityMonster) {
        // Boss death/flower handling runs first in EntityMonster.onDeath so failures
        // in quests or scripted challenges cannot suppress the claimable flower.
        if (entityMonster != null
                && WorldBossSpawnHelper.isWorldBossGroup(entityMonster.getGroupId())) {
            return;
        }
        if (entityMonster == null) {
            return;
        }
        Scene scene = entityMonster.getScene();
        if (!OpenWorldSpawnHelper.isOpenWorldScene(scene)) {
            return;
        }
        int n = OpenWorldSpawnHelper.resolveOwnerUid(scene);
        if (n <= 0) {
            return;
        }
        int n2 = entityMonster.getGroupId();
        int n3 = entityMonster.getConfigId();
        if (n2 <= 0 || n3 <= 0) {
            return;
        }
        int n4 = OpenWorldSpawnHelper.resolveRefreshId(scene, n2);
        long l = System.currentTimeMillis() / 1000L;
        String string = OpenWorldSpawnHelper.key(n, scene.getId(), n2, n3);
        LOCAL_COOLDOWN.put(string, l);
        try {
            int n5 = entityMonster.getMonsterData() != null ? entityMonster.getMonsterData().getId() : 0;
            Document document = new Document("ownerUid", n).append("sceneId", scene.getId()).append("groupId", n2).append("configId", n3).append("gadgetId", 0).append("monsterId", n5).append("gatherItemId", 0).append("posX", entityMonster.getPosition() != null ? entityMonster.getPosition().getX() : 0.0f).append("posY", entityMonster.getPosition() != null ? entityMonster.getPosition().getY() : 0.0f).append("posZ", entityMonster.getPosition() != null ? entityMonster.getPosition().getZ() : 0.0f).append("consumedAt", l).append("refreshId", n4);
            OpenWorldSpawnHelper.col().replaceOne(Filters.and(Filters.eq("ownerUid", n), Filters.eq("sceneId", scene.getId()), Filters.eq("groupId", n2), Filters.eq("configId", n3)), document, new ReplaceOptions().upsert(true));
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("OpenWorldSpawnHelper persist death failed uid={} group={} cfg={}: {}", new Object[]{n, n2, n3, throwable.toString()});
        }
        try {
            SceneGroupInstance sceneGroupInstance;
            if (scene.getScriptManager() != null && (sceneGroupInstance = scene.getScriptManager().getGroupInstanceById(n2)) != null) {
                sceneGroupInstance.getDeadEntities().add(n3);
                sceneGroupInstance.save();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static boolean isEntityRespawnBlocked(Scene scene, int n, int n2) {
        Document document;
        if (scene != null && DomainDungeonHelper.isDomainScene(scene)) {
            return false;
        }
        if (!OpenWorldSpawnHelper.isOpenWorldScene(scene) || n <= 0 || n2 <= 0) {
            return false;
        }
        int n3 = OpenWorldSpawnHelper.resolveOwnerUid(scene);
        if (n3 <= 0) {
            return false;
        }
        String string = OpenWorldSpawnHelper.key(n3, scene.getId(), n, n2);
        Long l = LOCAL_COOLDOWN.get(string);
        if (l != null) {
            Document document2;
            document = new Document("consumedAt", (Object)l).append("refreshId", (Object)OpenWorldSpawnHelper.resolveRefreshId(scene, n));
            Document document3 = null;
            try {
                document3 = (Document)OpenWorldSpawnHelper.col().find(Filters.and((Bson[])new Bson[]{Filters.eq((String)"ownerUid", (Object)n3), Filters.eq((String)"sceneId", (Object)scene.getId()), Filters.eq((String)"groupId", (Object)n), Filters.eq((String)"configId", (Object)n2)})).first();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            Document document4 = document2 = document3 != null ? document3 : document;
            if (!OpenWorldSpawnHelper.isExpired(scene, n, document2)) {
                return true;
            }
            LOCAL_COOLDOWN.remove(string);
        }
        try {
            document = (Document)OpenWorldSpawnHelper.col().find(Filters.and((Bson[])new Bson[]{Filters.eq((String)"ownerUid", (Object)n3), Filters.eq((String)"sceneId", (Object)scene.getId()), Filters.eq((String)"groupId", (Object)n), Filters.eq((String)"configId", (Object)n2)})).first();
            if (document == null) {
                return false;
            }
            if (OpenWorldSpawnHelper.isExpired(scene, n, document)) {
                OpenWorldSpawnHelper.clearGroupEntityDeathRecord(scene, n, n2);
                return false;
            }
            long l2 = OpenWorldSpawnHelper.readConsumedAt(document);
            if (l2 > 0L) {
                LOCAL_COOLDOWN.put(string, l2);
            }
            return true;
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("OpenWorldSpawnHelper cooldown check failed: {}", (Object)throwable.toString());
            return false;
        }
    }

    public static void clearGroupDeathRecords(Scene scene, int n) {
        if (scene == null || n <= 0) {
            return;
        }
        // Always clear in-memory deadEntities so world bosses can respawn after leaving a flower,
        // even when the scene briefly has no resolvable owner uid.
        try {
            SceneGroupInstance sceneGroupInstance;
            if (scene.getScriptManager() != null && (sceneGroupInstance = scene.getScriptManager().getGroupInstanceById(n)) != null) {
                sceneGroupInstance.getDeadEntities().clear();
                sceneGroupInstance.save();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        int n2 = OpenWorldSpawnHelper.resolveOwnerUid(scene);
        if (n2 <= 0) {
            return;
        }
        String string = n2 + ":" + scene.getId() + ":" + n + ":";
        LOCAL_COOLDOWN.keySet().removeIf(string2 -> string2.startsWith(string));
        try {
            OpenWorldSpawnHelper.col().deleteMany(Filters.and((Bson[])new Bson[]{Filters.eq((String)"ownerUid", (Object)n2), Filters.eq((String)"sceneId", (Object)scene.getId()), Filters.eq((String)"groupId", (Object)n)}));
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("OpenWorldSpawnHelper clearGroupDeathRecords failed: {}", (Object)throwable.toString());
        }
    }

    public static void clearGroupEntityDeathRecord(Scene scene, int n, int n2) {
        if (scene == null || n <= 0 || n2 <= 0) {
            return;
        }
        int n3 = OpenWorldSpawnHelper.resolveOwnerUid(scene);
        if (n3 <= 0) {
            return;
        }
        LOCAL_COOLDOWN.remove(OpenWorldSpawnHelper.key(n3, scene.getId(), n, n2));
        try {
            OpenWorldSpawnHelper.col().deleteMany(Filters.and((Bson[])new Bson[]{Filters.eq((String)"ownerUid", (Object)n3), Filters.eq((String)"sceneId", (Object)scene.getId()), Filters.eq((String)"groupId", (Object)n), Filters.eq((String)"configId", (Object)n2)}));
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("OpenWorldSpawnHelper clearGroupEntityDeathRecord failed: {}", (Object)throwable.toString());
        }
        try {
            SceneGroupInstance sceneGroupInstance;
            if (scene.getScriptManager() != null && (sceneGroupInstance = scene.getScriptManager().getGroupInstanceById(n)) != null) {
                sceneGroupInstance.getDeadEntities().remove(n2);
                sceneGroupInstance.save();
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    private static Set<SpawnDataEntry.GridBlockId> getLoadedGridBlocks(Scene scene) {
        try {
            Object object;
            Field field = loadedGridBlocksField;
            if (field == null) {
                field = Scene.class.getDeclaredField("loadedGridBlocks");
                field.setAccessible(true);
                loadedGridBlocksField = field;
            }
            if ((object = field.get(scene)) instanceof Set) {
                Set set;
                Set set2 = set = (Set)object;
                return set2;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    private static boolean isGatherGadgetInGroup(SceneGroup sceneGroup, int n) {
        if (sceneGroup == null || sceneGroup.gadgets == null) {
            return false;
        }
        try {
            SceneGadget sceneGadget = (SceneGadget)sceneGroup.gadgets.get(n);
            if (sceneGadget == null) {
                return false;
            }
            int n2 = sceneGadget.gadget_id;
            for (GatherData gatherData : GameData.getGatherDataMap().values()) {
                if (gatherData == null || gatherData.getGadgetId() != n2) continue;
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    private static void purgeExpiredGatherDeaths(Scene scene) {
        int n = OpenWorldSpawnHelper.resolveOwnerUid(scene);
        if (n <= 0 || scene.getScriptManager() == null) {
            return;
        }
        try {
            for (Document doc :
                    OpenWorldSpawnHelper.col()
                            .find(
                                    Filters.and(
                                            Filters.eq("ownerUid", n),
                                            Filters.eq("sceneId", scene.getId()),
                                            Filters.gt("gatherItemId", 0)))) {
                int n2 = ((Number) doc.get("groupId")).intValue();
                int n3 = ((Number) doc.get("configId")).intValue();
                if (!OpenWorldSpawnHelper.isExpired(scene, n2, doc)) {
                    continue;
                }
                OpenWorldSpawnHelper.clearGroupEntityDeathRecord(scene, n2, n3);
            }
            HashMap<Integer, SceneGroupInstance> hashMap = new HashMap<>();
            try {
                Map<Integer, SceneGroupInstance> cached =
                        scene.getScriptManager().getCachedGroupInstances();
                if (cached != null) {
                    hashMap.putAll(cached);
                }
            } catch (Throwable throwable) {
                // empty catch block
            }
            try {
                Field field =
                        scene.getScriptManager().getClass().getDeclaredField("sceneGroupsInstances");
                field.setAccessible(true);
                Object object3 = field.get(scene.getScriptManager());
                if (object3 instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) object3;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        Object object = entry.getValue();
                        if (!(object instanceof SceneGroupInstance)) {
                            continue;
                        }
                        SceneGroupInstance sceneGroupInstance = (SceneGroupInstance) object;
                        hashMap.put(sceneGroupInstance.getGroupId(), sceneGroupInstance);
                    }
                }
            } catch (Throwable throwable) {
                // empty catch block
            }
            for (SceneGroupInstance sceneGroupInstance2 : hashMap.values()) {
                if (sceneGroupInstance2 == null
                        || sceneGroupInstance2.getDeadEntities() == null
                        || sceneGroupInstance2.getDeadEntities().isEmpty()) {
                    continue;
                }
                SceneGroup sceneGroup = sceneGroupInstance2.getLuaGroup();
                if (sceneGroup == null) {
                    try {
                        sceneGroup =
                                scene.getScriptManager().getGroupById(sceneGroupInstance2.getGroupId());
                    } catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                HashSet<Integer> hashSet = new HashSet<>(sceneGroupInstance2.getDeadEntities());
                boolean bl = false;
                for (Integer configId : hashSet) {
                    if (configId == null || configId <= 0) {
                        continue;
                    }
                    if (!OpenWorldSpawnHelper.isGatherGadgetInGroup(sceneGroup, configId)) {
                        continue;
                    }
                    Document document =
                            OpenWorldSpawnHelper.col()
                                    .find(
                                            Filters.and(
                                                    Filters.eq("ownerUid", n),
                                                    Filters.eq("sceneId", scene.getId()),
                                                    Filters.eq(
                                                            "groupId", sceneGroupInstance2.getGroupId()),
                                                    Filters.eq("configId", configId)))
                                    .first();
                    if (document == null
                            || !OpenWorldSpawnHelper.isExpired(
                                    scene, sceneGroupInstance2.getGroupId(), document)) {
                        continue;
                    }
                    OpenWorldSpawnHelper.clearGroupEntityDeathRecord(
                            scene, sceneGroupInstance2.getGroupId(), configId);
                    bl = true;
                }
                if (!bl) {
                    continue;
                }
                try {
                    sceneGroupInstance2.save();
                } catch (Throwable throwable) {
                }
            }
        } catch (Throwable throwable) {
            Grasscutter.getLogger()
                    .warn(
                            "OpenWorldSpawnHelper purgeExpiredGatherDeaths failed: {}",
                            (Object) throwable.toString());
        }
    }

    public static void syncGridSpawnCooldowns(Scene scene) {
        if (!OpenWorldSpawnHelper.isOpenWorldScene(scene)) {
            return;
        }
        if (OpenWorldSpawnHelper.resolveOwnerUid(scene) <= 0) {
            return;
        }
        try {
            OpenWorldSpawnHelper.purgeExpiredGatherDeaths(scene);
            Set<SpawnDataEntry> set = scene.getDeadSpawnedEntities();
            if (set == null) {
                return;
            }
            set.removeIf(
                    spawnDataEntry -> {
                        if (spawnDataEntry == null || spawnDataEntry.getGroup() == null) {
                            return false;
                        }
                        boolean isMonster = spawnDataEntry.getMonsterId() > 0;
                        boolean isGather =
                                spawnDataEntry.getGatherItemId() > 0
                                        || spawnDataEntry.getGadgetId() > 0;
                        if (!isMonster && !isGather) {
                            return false;
                        }
                        return !OpenWorldSpawnHelper.isEntityRespawnBlocked(
                                scene,
                                spawnDataEntry.getGroup().getGroupId(),
                                spawnDataEntry.getConfigId());
                    });
            Set<SpawnDataEntry.GridBlockId> set2 = OpenWorldSpawnHelper.getLoadedGridBlocks(scene);
            if (set2 == null) {
                return;
            }
            HashMap hashMap = GameDepot.getSpawnLists();
            if (hashMap == null) {
                return;
            }
            for (SpawnDataEntry.GridBlockId gridBlockId : set2) {
                Object v = hashMap.get(gridBlockId);
                if (!(v instanceof ArrayList)) continue;
                ArrayList arrayList = (ArrayList)v;
                for (Object e : arrayList) {
                    boolean bl;
                    SpawnDataEntry spawnDataEntry2;
                    if (!(e instanceof SpawnDataEntry) || (spawnDataEntry2 = (SpawnDataEntry)e).getGroup() == null || !(bl = spawnDataEntry2.getMonsterId() > 0 || spawnDataEntry2.getGatherItemId() > 0 || spawnDataEntry2.getGadgetId() > 0) || !OpenWorldSpawnHelper.isEntityRespawnBlocked(scene, spawnDataEntry2.getGroup().getGroupId(), spawnDataEntry2.getConfigId())) continue;
                    set.add(spawnDataEntry2);
                }
            }
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("OpenWorldSpawnHelper syncGridSpawnCooldowns failed: {}", (Object)throwable.toString());
        }
    }
}

