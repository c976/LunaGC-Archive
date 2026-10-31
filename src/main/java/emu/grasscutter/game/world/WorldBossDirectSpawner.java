/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.GameData
 *  emu.grasscutter.data.excels.monster.MonsterData
 *  emu.grasscutter.game.entity.EntityMonster
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.world.Position
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.scripts.data.SceneMonster
 */
package emu.grasscutter.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.game.world.WorldBossSpawnHelper;
import emu.grasscutter.scripts.data.SceneMonster;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldBossDirectSpawner {
    private static final float SPAWN_DISTANCE = 220.0f;
    private static final long RETRY_MS = 5000L;
    private static final List<BossDef> BOSSES = new ArrayList<BossDef>();
    private static final Map<Integer, Long> LAST_ATTEMPT_MS = new ConcurrentHashMap<Integer, Long>();
    private static volatile boolean loaded;

    private WorldBossDirectSpawner() {
    }

    public static void ensureNearby(Player player) {
        if (player == null || player.getScene() == null || player.getPosition() == null) {
            return;
        }
        Scene scene = player.getScene();
        WorldBossDirectSpawner.ensureLoaded();
        Position pos = player.getPosition();
        long now = System.currentTimeMillis();
        for (BossDef bossDef : BOSSES) {
            if (bossDef.sceneIdHint > 0 && scene.getId() != bossDef.sceneIdHint) {
                continue;
            }
            float dx = pos.getX() - bossDef.x;
            float dy = pos.getY() - bossDef.y;
            float dz = pos.getZ() - bossDef.z;
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > SPAWN_DISTANCE * SPAWN_DISTANCE) {
                continue;
            }
            try {
                if (BossFlowerGuard.hasUnclaimedFlower(scene, bossDef.groupId, bossDef.x, bossDef.y, bossDef.z)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (WorldBossSpawnHelper.isBossRespawnBlocked(scene, bossDef.groupId)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            if (WorldBossDirectSpawner.hasAliveBoss(scene, bossDef.monsterId, bossDef.x, bossDef.y, bossDef.z)) {
                WorldBossDirectSpawner.dedupeNearSpawn(scene, bossDef);
                continue;
            }
            Long last = LAST_ATTEMPT_MS.get(bossDef.groupId);
            if (last != null && now - last < RETRY_MS) {
                continue;
            }
            LAST_ATTEMPT_MS.put(bossDef.groupId, now);
            float dist = (float) Math.sqrt(distSq);
            WorldBossDirectSpawner.spawnOne(scene, player, bossDef, dist);
        }
    }

    /**
     * Last-resort spawn used by WorldBossSpawnHelper when group/script paths fail.
     */
    public static void spawnBossAt(Scene scene, int groupId, int monsterId, float x, float y, float z) {
        if (scene == null || groupId <= 0 || monsterId <= 0) {
            return;
        }
        WorldBossDirectSpawner.ensureLoaded();
        if (WorldBossDirectSpawner.hasAliveBoss(scene, monsterId, x, y, z)) {
            return;
        }
        try {
            if (BossFlowerGuard.hasUnclaimedFlower(scene, groupId, x, y, z)) {
                return;
            }
        } catch (Throwable ignored) {
        }
        BossDef bossDef = null;
        for (BossDef b : BOSSES) {
            if (b.groupId == groupId) {
                bossDef = b;
                break;
            }
        }
        if (bossDef == null) {
            bossDef = new BossDef(groupId, monsterId, x, y, z, scene.getId(), "fallback-" + groupId);
        }
        Player player = scene.getPlayers().isEmpty() ? null : scene.getPlayers().iterator().next();
        float dist = 0.0f;
        if (player != null && player.getPosition() != null) {
            float dx = player.getPosition().getX() - x;
            float dy = player.getPosition().getY() - y;
            float dz = player.getPosition().getZ() - z;
            dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        WorldBossDirectSpawner.spawnOne(scene, player, bossDef, dist);
    }

    private static void dedupeNearSpawn(Scene scene, BossDef bossDef) {
        ArrayList<EntityMonster> arrayList = new ArrayList<EntityMonster>();
        for (GameEntity gameEntity : scene.getEntities().values()) {
            float f;
            float f2;
            float f3;
            Position position;
            EntityMonster entityMonster3;
            if (!(gameEntity instanceof EntityMonster) || !(entityMonster3 = (EntityMonster)gameEntity).isAlive() || (position = entityMonster3.getPosition()) == null || (f3 = position.getX() - bossDef.x) * f3 + (f2 = position.getY() - bossDef.y) * f2 + (f = position.getZ() - bossDef.z) * f > 10000.0f) continue;
            int n = entityMonster3.getEntityTypeId();
            if (n == bossDef.monsterId || n == WorldBossDirectSpawner.relatedMonsterId(bossDef.monsterId) || WorldBossDirectSpawner.isSameBossFamily(bossDef.monsterId, n)) {
                arrayList.add(entityMonster3);
                continue;
            }
            if (!(f3 * f3 + f2 * f2 + f * f < 625.0f)) continue;
            arrayList.add(entityMonster3);
        }
        if (arrayList.size() <= 1) {
            return;
        }
        arrayList.sort((entityMonster, entityMonster2) -> {
            boolean bl;
            boolean bl2 = entityMonster.getEntityTypeId() == bossDef.monsterId;
            boolean bl3 = bl = entityMonster2.getEntityTypeId() == bossDef.monsterId;
            if (bl2 != bl) {
                return bl2 ? -1 : 1;
            }
            return Integer.compare(entityMonster2.getLevel(), entityMonster.getLevel());
        });
        for (int i = 1; i < arrayList.size(); ++i) {
            try {
                scene.removeEntity((GameEntity)arrayList.get(i));
                Grasscutter.getLogger().info("WorldBossDirectSpawner removed duplicate monster {} entityId={} at boss group {}", new Object[]{((EntityMonster)arrayList.get(i)).getEntityTypeId(), ((EntityMonster)arrayList.get(i)).getId(), bossDef.groupId});
                continue;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private static int relatedMonsterId(int n) {
        if (n == 22100101) {
            return 22100201;
        }
        if (n == 22100201) {
            return 22100101;
        }
        return -1;
    }

    private static boolean isSameBossFamily(int n, int n2) {
        return WorldBossDirectSpawner.relatedMonsterId(n) == n2;
    }

    private static void spawnOne(Scene scene, Player player, BossDef bossDef, float f) {
        block21: {
            block20: {
                try {
                    MonsterData monsterData = (MonsterData)GameData.getMonsterDataMap().get(bossDef.monsterId);
                    if (monsterData == null) {
                        Grasscutter.getLogger().warn("WorldBossDirectSpawner missing MonsterData {} group {}", (Object)bossDef.monsterId, (Object)bossDef.groupId);
                        break block20;
                    }
                    int n = Math.max(1, WorldBossSpawnHelper.resolveWorldBossLevel(scene));
                    if (n <= 0) {
                        n = 36;
                    }
                    Position position = new Position(bossDef.x, bossDef.y, bossDef.z);
                    Position position2 = new Position(0.0f, 0.0f, 0.0f);
                    EntityMonster entityMonster = new EntityMonster(scene, monsterData, position, position2, n);
                    entityMonster.setPoseId(101);
                    try {
                        SceneMonster sceneMonster = new SceneMonster();
                        sceneMonster.config_id = Math.floorMod(bossDef.groupId, 100000);
                        if (sceneMonster.config_id <= 0) {
                            sceneMonster.config_id = 1;
                        }
                        sceneMonster.monster_id = bossDef.monsterId;
                        sceneMonster.level = n;
                        sceneMonster.pose_id = 101;
                        sceneMonster.drop_id = 1000100;
                        sceneMonster.pos = position.clone();
                        sceneMonster.rot = position2.clone();
                        entityMonster.setMetaMonster(sceneMonster);
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                    scene.addEntity((GameEntity)entityMonster);
                    Grasscutter.getLogger().info("WorldBossDirectSpawner spawned monster {} group {} for uid={} ({}m) at {}, {}, {}", new Object[]{bossDef.monsterId, bossDef.groupId, player.getUid(), String.format("%.1f", Float.valueOf(f)), Float.valueOf(bossDef.x), Float.valueOf(bossDef.y), Float.valueOf(bossDef.z)});
                }
                catch (Throwable throwable) {
                    Grasscutter.getLogger().warn("WorldBossDirectSpawner failed group {} monster {}: {}", new Object[]{bossDef.groupId, bossDef.monsterId, throwable.toString()});
                }
            }
            Object var11_12 = null;
            try {
                if (scene != null && bossDef != null) {
                    int n = bossDef.monsterId;
                    Object object = GameData.getMonsterDataMap().get(n);
                    MonsterData monsterData = (MonsterData)object;
                    if (monsterData != null && monsterData.getDescribeData() != null && monsterData.getSpecialNameId() != 0) {
                        int n2 = monsterData.getDescribeData().getTitleId();
                        int n3 = monsterData.getSpecialNameId();
                        for (Object v : scene.getEntities().values()) {
                            float f2;
                            float f3;
                            float f4;
                            EntityMonster entityMonster;
                            if (!(v instanceof EntityMonster) || (entityMonster = (EntityMonster)v).getMonsterData() == null || entityMonster.getMonsterData().getId() != n || (f4 = entityMonster.getPosition().getX() - bossDef.x) * f4 + (f3 = entityMonster.getPosition().getY() - bossDef.y) * f3 + (f2 = entityMonster.getPosition().getZ() - bossDef.z) * f2 > 64.0f) continue;
                            SceneMonster sceneMonster = entityMonster.getMetaMonster();
                            if (sceneMonster == null) {
                                sceneMonster = new SceneMonster();
                                sceneMonster.monster_id = n;
                            }
                            if (sceneMonster.special_name_id != 0) continue;
                            sceneMonster.title_id = n2;
                            sceneMonster.special_name_id = n3;
                            entityMonster.setMetaMonster(sceneMonster);
                        }
                    }
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            Object var26_26 = null;
            try {
                if (scene == null || bossDef == null) break block21;
                int n = bossDef.monsterId;
                MonsterData monsterData = (MonsterData)GameData.getMonsterDataMap().get(n);
                int n4 = 0;
                int n5 = 0;
                if (monsterData != null && monsterData.getDescribeData() != null) {
                    n4 = monsterData.getDescribeData().getTitleId();
                    n5 = monsterData.getSpecialNameId();
                }
                if (n == 22100101 || n == 22100201 || n == 22100301 || n == 22100401) {
                    n4 = 4071;
                    n5 = 10253;
                }
                if (n4 != 0 && n5 != 0) {
                    for (Object v : scene.getEntities().values()) {
                        float f5;
                        float f6;
                        float f7;
                        EntityMonster entityMonster;
                        if (!(v instanceof EntityMonster) || (entityMonster = (EntityMonster)v).getMonsterData() == null || entityMonster.getMonsterData().getId() != n || (f7 = entityMonster.getPosition().getX() - bossDef.x) * f7 + (f6 = entityMonster.getPosition().getY() - bossDef.y) * f6 + (f5 = entityMonster.getPosition().getZ() - bossDef.z) * f5 > 64.0f) continue;
                        SceneMonster sceneMonster = entityMonster.getMetaMonster();
                        if (sceneMonster == null) {
                            sceneMonster = new SceneMonster();
                            sceneMonster.monster_id = n;
                        }
                        sceneMonster.title_id = n4;
                        sceneMonster.special_name_id = n5;
                        entityMonster.setMetaMonster(sceneMonster);
                    }
                }
            }
            catch (Throwable throwable) {}
        }
    }

    private static boolean hasAliveBoss(Scene scene, int n, float f, float f2, float f3) {
        int n2 = WorldBossDirectSpawner.relatedMonsterId(n);
        for (GameEntity gameEntity : scene.getEntities().values()) {
            int n3;
            float f4;
            float f5;
            float f6;
            Position position;
            EntityMonster entityMonster;
            if (!(gameEntity instanceof EntityMonster) || !(entityMonster = (EntityMonster)gameEntity).isAlive() || (position = entityMonster.getPosition()) == null || (f6 = position.getX() - f) * f6 + (f5 = position.getY() - f2) * f5 + (f4 = position.getZ() - f3) * f4 >= 6400.0f || (n3 = entityMonster.getEntityTypeId()) != n && n3 != n2 && !(f6 * f6 + f5 * f5 + f4 * f4 < 625.0f)) continue;
            return true;
        }
        return false;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        WorldBossDirectSpawner.loadFile(Path.of("resources", "patch", "world-boss-coords.json"), false);
        WorldBossDirectSpawner.loadFile(Path.of("resources", "patch", "nod-krai-world-bosses.json"), true);
        WorldBossDirectSpawner.addIfMissing(133009050, 24111101, 4200.19f, 91.27009f, -258.42786f, 3, "WatcherPrimo-handbook");
        Grasscutter.getLogger().info("WorldBossDirectSpawner loaded {} boss defs", (Object)BOSSES.size());
    }

    private static void loadFile(Path path, boolean bl) {
        try {
            if (!Files.isRegularFile(path, new LinkOption[0])) {
                return;
            }
            JsonArray jsonArray = JsonParser.parseReader((Reader)Files.newBufferedReader(path, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement jsonElement : jsonArray) {
                JsonObject jsonObject;
                if (!jsonElement.isJsonObject() || !(jsonObject = jsonElement.getAsJsonObject()).has("groupId") || !jsonObject.has("monsterId") || !jsonObject.has("x")) continue;
                int n = jsonObject.get("groupId").getAsInt();
                int n2 = jsonObject.get("monsterId").getAsInt();
                float f = jsonObject.get("x").getAsFloat();
                float f2 = jsonObject.get("y").getAsFloat();
                float f3 = jsonObject.get("z").getAsFloat();
                int n3 = 3;
                if (n >= 155000000 && n < 166000000) {
                    n3 = 5;
                } else if (n >= 166000000 && n < 170000000) {
                    n3 = 6;
                }
                String string = bl && jsonObject.has("name") ? jsonObject.get("name").getAsString() : "g" + n;
                WorldBossDirectSpawner.addIfMissing(n, n2, f, f2, f3, n3, string);
            }
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("WorldBossDirectSpawner failed to load {}: {}", (Object)path, (Object)throwable.toString());
        }
    }

    private static void addIfMissing(int n, int n2, float f, float f2, float f3, int n3, String string) {
        for (BossDef bossDef : BOSSES) {
            if (bossDef.groupId != n) continue;
            return;
        }
        BOSSES.add(new BossDef(n, n2, f, f2, f3, n3, string));
    }

    private static final class BossDef {
        final int groupId;
        final int monsterId;
        final float x;
        final float y;
        final float z;
        final int sceneIdHint;
        final String name;

        BossDef(int n, int n2, float f, float f2, float f3, int n3, String string) {
            this.groupId = n;
            this.monsterId = n2;
            this.x = f;
            this.y = f2;
            this.z = f3;
            this.sceneIdHint = n3;
            this.name = string;
        }
    }
}

