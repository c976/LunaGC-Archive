package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.InvestigationMonsterData;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass.InvestigationMonster;
import emu.grasscutter.net.proto.InvestigationMonsterConfig._InvestigationMonsterConfig;
import emu.grasscutter.net.proto.InvestigationMonsterDetail._InvestigationMonsterDetail;
import emu.grasscutter.scripts.data.SceneBossChest;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMonster;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;

/**
 * Resolve handbook track pos: world bosses use WorldBossSpawnHelper fixed coords;
 * elites use live / nearest camp. Live boss tracking made map marks wander with AI.
 */
public final class InvestigationTrackHelper {
    private static final Map<String, SceneGroup> CACHE = new ConcurrentHashMap<String, SceneGroup>();

    private InvestigationTrackHelper() {}

    public static final class TrackTarget {
        public int sceneId;
        public int groupId;
        public SceneMonster monster;
        public SceneGroup group;
        public boolean fromLive;
        public boolean synthetic;
        public boolean allOnCooldown;
        public int nextRefreshUnix;
    }

    private static int sceneIdForGroup(int groupId) {
        if (groupId >= 610000000 && groupId < 620000000) return 103;
        if (groupId >= 511000000 && groupId < 512000000) return 5;
        if (groupId >= 166000000 && groupId < 170000000) return 6;
        if (groupId >= 155000000 && groupId < 166000000) return 5;
        if (groupId >= 144000000 && groupId < 150000000) return 4;
        return 3;
    }

    private static int inferSceneId(InvestigationMonsterData data, int firstGroupId) {
        int fromGroup = sceneIdForGroup(firstGroupId);
        if (fromGroup != 3) return fromGroup;
        try {
            if (data != null && data.getCityData() != null) {
                int sid = data.getCityData().getSceneId();
                if (sid > 0) return sid;
            }
        } catch (Throwable ignored) {
        }
        return 3;
    }

    public static void invalidateGroup(int groupId) {
        if (groupId <= 0) return;
        for (String k : CACHE.keySet().toArray(new String[0])) {
            if (k.endsWith("_" + groupId)) CACHE.remove(k);
        }
    }

    /** Drop all cached group scripts (e.g. after fixing corrupted parallel Lua loads). */
    public static void clearCache() {
        CACHE.clear();
    }

    private static SceneGroup loadGroup(int sceneId, int groupId) {
        String key = sceneId + "_" + groupId;
        SceneGroup cached = CACHE.get(key);
        if (cached != null) {
            if (cached.monsters == null || cached.monsters.isEmpty()) {
                CACHE.remove(key);
            } else {
                return cached;
            }
        }
        try {
            SceneGroup g = SceneGroup.of(groupId).load(sceneId);
            if (g != null) {
                CACHE.put(key, g);
            }
            return g;
        } catch (LuaError e) {
            Grasscutter.getLogger()
                    .error("InvestigationTrackHelper failed group {} scene {}: {}", groupId, sceneId, e.toString());
            return null;
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .error("InvestigationTrackHelper failed group {} scene {}: {}", groupId, sceneId, t.toString());
            return null;
        }
    }

    private static boolean onCooldown(int groupId) {
        try {
            return InvestigationSpawnHelper.isGroupOnKillCooldown(groupId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * World-boss map pins must use the same fixed coords as WorldBossSpawnHelper.
     * Lua group loads are racy under parallel handbook queries and can cache wrong monsters.
     */
    private static TrackTarget resolveBossTrack(
            InvestigationMonsterData data,
            List<Integer> groups,
            List<Integer> monsterIds,
            int sceneId) {
        boolean allCd = true;
        for (Integer gidObj : groups) {
            if (!onCooldown(gidObj.intValue())) {
                allCd = false;
                break;
            }
        }

        // 1) Patched / excel spawn (same source as live boss arenas)
        WorldBossSpawnHelper.PatchedBossSpawn patched = null;
        int patchedGid = 0;
        for (Integer gidObj : groups) {
            int gid = gidObj.intValue();
            if (!allCd && onCooldown(gid)) continue;
            try {
                WorldBossSpawnHelper.PatchedBossSpawn p =
                        WorldBossSpawnHelper.getPatchedBossSpawn(gid);
                if (p != null && p.position != null) {
                    patched = p;
                    patchedGid = gid;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        if (patched == null) {
            for (Integer gidObj : groups) {
                int gid = gidObj.intValue();
                try {
                    WorldBossSpawnHelper.PatchedBossSpawn p =
                            WorldBossSpawnHelper.getPatchedBossSpawn(gid);
                    if (p != null && p.position != null) {
                        patched = p;
                        patchedGid = gid;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        if (patched != null) {
            int sid = sceneIdForGroup(patchedGid);
            if (sid == 3) sid = sceneId;
            TrackTarget t = new TrackTarget();
            t.sceneId = sid;
            t.groupId = patched.groupId > 0 ? patched.groupId : patchedGid;
            t.group = loadGroup(t.sceneId, t.groupId);
            t.allOnCooldown = allCd;
            if (allCd) {
                try {
                    t.nextRefreshUnix = InvestigationSpawnHelper.getEarliestRespawnUnix(data.getId());
                } catch (Throwable ignored) {
                    t.nextRefreshUnix = 0;
                }
            }
            SceneMonster sm = new SceneMonster();
            int mid = patched.monsterId > 0 ? patched.monsterId : monsterIds.get(0).intValue();
            sm.monster_id = mid;
            sm.pos = patched.position.clone();
            sm.level = 36;
            t.monster = sm;
            return t;
        }

        // 2) Exact monster_id match from group script only (no "any monster" fallback)
        for (Integer midObj : monsterIds) {
            int mid = midObj.intValue();
            for (Integer gidObj : groups) {
                int gid = gidObj.intValue();
                if (!allCd && onCooldown(gid)) continue;
                int sid = sceneIdForGroup(gid);
                SceneGroup g = loadGroup(sid, gid);
                if (g == null || g.monsters == null) continue;
                for (SceneMonster sm : g.monsters.values()) {
                    if (sm == null || sm.monster_id != mid || sm.pos == null) continue;
                    TrackTarget t = new TrackTarget();
                    t.sceneId = sid == 3 ? sceneId : sid;
                    t.groupId = gid;
                    t.group = g;
                    t.monster = sm;
                    t.allOnCooldown = allCd;
                    if (allCd) {
                        try {
                            t.nextRefreshUnix =
                                    InvestigationSpawnHelper.getEarliestRespawnUnix(data.getId());
                        } catch (Throwable ignored) {
                            t.nextRefreshUnix = 0;
                        }
                    }
                    return t;
                }
            }
        }

        return null;
    }

    public static TrackTarget resolve(Player player, InvestigationMonsterData data) {
        if (data == null
                || data.getGroupIdList() == null
                || data.getGroupIdList().isEmpty()
                || data.getMonsterIdList() == null
                || data.getMonsterIdList().isEmpty()
                || data.getCityData() == null) {
            return null;
        }

        List<Integer> groups = data.getGroupIdList();
        List<Integer> monsterIds = data.getMonsterIdList();
        boolean isBoss = "Boss".equals(data.getMonsterCategory());

        int sceneId = inferSceneId(data, groups.get(0).intValue());
        Position ppos = player != null ? player.getPosition() : null;

        if (isBoss) {
            return resolveBossTrack(data, groups, monsterIds, sceneId);
        }

        // 1) Live entity — elites only. World bosses skip this so map pins stay on spawn pos.
        try {
            if (player != null && player.getScene() != null && ppos != null) {
                EntityMonster best = null;
                double bestDist = Double.MAX_VALUE;
                for (GameEntity ge : player.getScene().getEntities().values()) {
                    if (!(ge instanceof EntityMonster)) continue;
                    EntityMonster em = (EntityMonster) ge;
                    if (em.getMonsterData() == null || !em.isAlive()) continue;
                    int mid = em.getMonsterData().getId();
                    if (!monsterIds.contains(Integer.valueOf(mid))) continue;
                    if (onCooldown(em.getGroupId())) continue;
                    double d = ppos.computeDistance(em.getPosition());
                    if (d < bestDist) {
                        bestDist = d;
                        best = em;
                    }
                }
                if (best != null) {
                    TrackTarget t = new TrackTarget();
                    t.fromLive = true;
                    t.sceneId = player.getScene().getId();
                    t.groupId = best.getGroupId();
                    SceneMonster sm = new SceneMonster();
                    sm.monster_id = best.getMonsterData().getId();
                    sm.pos = best.getPosition().clone();
                    sm.level = best.getLevel();
                    t.monster = sm;
                    return t;
                }
            }
        } catch (Throwable ignored) {
        }

        // 2) Nearest available script spawn among groups NOT on 12h cooldown
        TrackTarget bestScript = null;
        double bestScriptDist = Double.MAX_VALUE;
        for (Integer midObj : monsterIds) {
            int mid = midObj.intValue();
            for (Integer gidObj : groups) {
                int gid = gidObj.intValue();
                if (onCooldown(gid)) continue;
                SceneGroup g = loadGroup(sceneIdForGroup(gid), gid);
                if (g == null || g.monsters == null) continue;
                for (SceneMonster sm : g.monsters.values()) {
                    if (sm == null || sm.monster_id != mid || sm.pos == null) continue;
                    double d = ppos != null ? ppos.computeDistance(sm.pos) : 0.0;
                    if (bestScript == null || d < bestScriptDist) {
                        bestScriptDist = d;
                        TrackTarget t = new TrackTarget();
                        t.sceneId = sceneIdForGroup(gid);
                        t.groupId = gid;
                        t.group = g;
                        t.monster = sm;
                        bestScript = t;
                    }
                }
            }
        }

        // 2b) Index fallback (Common/Elite only)
        try {
            InvestigationSpawnHelper.SpawnEntry se =
                    InvestigationSpawnHelper.findNearestAvailable(data.getId(), ppos);
            if (se != null && se.position != null) {
                double d = ppos != null ? ppos.computeDistance(se.position) : 0.0;
                if (bestScript == null || d + 0.5 < bestScriptDist) {
                    TrackTarget t = new TrackTarget();
                    t.sceneId = sceneId;
                    t.groupId = se.groupId;
                    SceneMonster sm = new SceneMonster();
                    sm.monster_id = se.monsterId;
                    sm.pos = se.position.clone();
                    sm.level = 36;
                    t.monster = sm;
                    bestScript = t;
                    bestScriptDist = d;
                }
            }
        } catch (Throwable ignored) {
        }

        if (bestScript != null) {
            Grasscutter.getLogger()
                    .debug(
                            "InvestigationTrackHelper track group {} (nearest available, dist~{}m)",
                            bestScript.groupId,
                            (int) bestScriptDist);
            return bestScript;
        }

        // 3) Any group with monsters (even if first unmatched id)
        for (Integer gidObj : groups) {
            int gid = gidObj.intValue();
            if (onCooldown(gid)) continue;
            SceneGroup g = loadGroup(sceneIdForGroup(gid), gid);
            if (g == null || g.monsters == null || g.monsters.isEmpty()) continue;
            for (SceneMonster sm : g.monsters.values()) {
                if (sm == null || sm.pos == null) continue;
                TrackTarget t = new TrackTarget();
                t.sceneId = sceneIdForGroup(gid);
                t.groupId = gid;
                t.group = g;
                t.monster = sm;
                return t;
            }
        }

        // 4) All on cooldown / indexed position fallback
        boolean allCd = true;
        for (Integer gidObj : groups) {
            if (!onCooldown(gidObj.intValue())) {
                allCd = false;
                break;
            }
        }

        Position fallbackPos = null;
        int fallbackLevel = 0;
        int fallbackGid = groups.get(0).intValue();
        SceneGroup fallbackGroup = null;
        int fallbackScene = sceneId;

        for (Integer gidObj : groups) {
            int gid = gidObj.intValue();
            try {
                Position indexed = InvestigationSpawnHelper.getIndexedPosition(gid);
                if (indexed != null) {
                    fallbackPos = indexed;
                    fallbackGid = gid;
                    break;
                }
            } catch (Throwable ignored) {
            }
        }

        if (fallbackPos == null) {
            for (Integer gidObj : groups) {
                int gid = gidObj.intValue();
                SceneGroup g = loadGroup(sceneIdForGroup(gid), gid);
                if (g == null || g.monsters == null) continue;
                for (SceneMonster sm : g.monsters.values()) {
                    if (sm == null || sm.pos == null) continue;
                    fallbackPos = sm.pos.clone();
                    fallbackLevel = sm.level;
                    fallbackGid = gid;
                    fallbackGroup = g;
                    fallbackScene = sceneIdForGroup(gid);
                    break;
                }
                if (fallbackPos != null) break;
            }
        }

        if (fallbackPos == null) return null;

        TrackTarget t = new TrackTarget();
        t.synthetic = true;
        t.sceneId = fallbackScene;
        t.groupId = fallbackGid;
        t.group = fallbackGroup;
        t.allOnCooldown = allCd;
        if (allCd) {
            try {
                t.nextRefreshUnix = InvestigationSpawnHelper.getEarliestRespawnUnix(data.getId());
            } catch (Throwable ignored) {
                t.nextRefreshUnix = 0;
            }
        }
        SceneMonster sm = new SceneMonster();
        sm.monster_id = monsterIds.get(0).intValue();
        sm.pos = fallbackPos;
        sm.level = fallbackLevel > 0 ? fallbackLevel : 36;
        t.monster = sm;
        return t;
    }

    private static int resolveMonsterLevel(Player player, int base) {
        int level = base > 0 ? base : 36;
        try {
            if (player != null && player.getWorld() != null) {
                var map = GameData.getWorldLevelDataMap();
                if (map != null) {
                    WorldLevelData wld = map.get(player.getWorld().getWorldLevel());
                    if (wld != null) {
                        level = Math.max(level, wld.getMonsterLevel());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return level;
    }

    public static InvestigationMonster buildInvestigationMonster(Player player, InvestigationMonsterData data) {
        TrackTarget t = resolve(player, data);
        if (t == null || t.monster == null || t.monster.pos == null) {
            return null;
        }
        Position pos = t.monster.pos;
        if (Math.abs(pos.getX()) < 5.0f && Math.abs(pos.getZ()) < 5.0f) {
            return null;
        }

        int level = resolveMonsterLevel(player, t.monster.level);
        boolean alive = !t.allOnCooldown;
        int nextRefresh = alive ? Integer.MAX_VALUE : (t.nextRefreshUnix > 0 ? t.nextRefreshUnix : 0);
        int refreshInterval = alive ? Integer.MAX_VALUE : InvestigationSpawnHelper.getEliteRespawnSeconds();

        InvestigationMonster.Builder builder =
                InvestigationMonster.newBuilder().setId(data.getId()).setCityId(data.getCityId());

        _InvestigationMonsterDetail.Builder detail =
                _InvestigationMonsterDetail.newBuilder()
                        .setMonsterConfig(
                                _InvestigationMonsterConfig.newBuilder()
                                        .setSceneId(t.sceneId)
                                        .setGroupId(t.groupId)
                                        .setMonsterId(t.monster.monster_id))
                        .setLevel(level)
                        .setIsAlive(alive)
                        .setIsRespawning(!alive)
                        .setNextRefreshTime(nextRefresh)
                        .setRefreshInterval(refreshInterval)
                        .setPos(t.monster.pos.toProto());

        if ("Boss".equals(data.getMonsterCategory())) {
            int resin = 40;
            int maxChest = 3;
            try {
                if (!t.synthetic && !t.fromLive && t.group != null) {
                    Optional<SceneBossChest> chest = t.group.searchBossChestInGroup();
                    if (chest != null && chest.isPresent()) {
                        resin = chest.get().resin;
                        maxChest = chest.get().take_num;
                    }
                }
            } catch (Throwable ignored) {
            }
            detail.setResin(resin).setMaxBossChestNum(maxChest);
        }

        builder.addInvestigationMonsterDetailList(detail);
        return builder.build();
    }
}
