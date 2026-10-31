package emu.grasscutter.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.PacketMonsterForceAlertNotify;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawn Snezhnaya exploration markers (cryo oculi and chests) from
 * data/snezhnaya_explore_points.json near players on scene 3.
 *
 * Chest guards: Fatui skirmisher / agent placeholders until official
 * Snezhnaya spawn tables exist on this build.
 */
public final class SnezhnayaExploreSpawnHelper {
    private static final double NEAR_DIST = 120.0;
    private static final double LEAVE_DIST = 200.0;
    private static final long CHECK_INTERVAL_MS = 2500L;
    /** Separate from Nod-Krai (910700000) so claim ids never collide. */
    private static final int SYNTH_GROUP_BASE = 910800000;
    private static final Path POINTS_FILE = Path.of("data", "snezhnaya_explore_points.json");
    private static final Path CLAIMED_FILE = Path.of("data", "snezhnaya_explore_claimed.json");
    private static final Path GUARD_KILL_FILE = Path.of("data", "snezhnaya_guard_respawn.json");
    /** Same as the bounty system: 12h after a camp wipe before guards respawn. */
    private static final long GUARD_RESPAWN_MS = 12L * 60L * 60L * 1000L;

    // Common chests: Fatui skirmishers / agents
    private static final int[] COMMON_GUARD_POOL =
            new int[] {
                23020101, 23020102, 23021101, 23021102,
                23030101, 23030102, 23040101, 23040102, 23050101
            };
    // Exquisite / precious / remarkable: Fatui operatives
    private static final int[] ELITE_GUARD_POOL =
            new int[] {
                23060101, 23060201, 23060301, 23060401, 23060501,
                25080101, 25080201, 25080301
            };
    // Luxurious: stronger Fatui elites
    private static final int[] LUX_GUARD_POOL =
            new int[] {25080101, 25080201, 25080301, 25080401, 23060501};

    /** Approximate open-world band for current Snezhnaya map coords. */
    private static final float REGION_MIN_X = 7000.0f;
    private static final float REGION_MAX_X = 11000.0f;
    private static final float REGION_MIN_Z = 5200.0f;
    private static final float REGION_MAX_Z = 8800.0f;

    private static volatile List<ExplorePoint> points;
    private static final ConcurrentHashMap<Integer, Long> lastCheckMs = new ConcurrentHashMap<Integer, Long>();
    private static final Set<Integer> claimed = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> held = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> guardHeld = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<Integer, Long> guardKilledAtMs = new ConcurrentHashMap<Integer, Long>();
    private static volatile boolean claimedLoaded;
    private static volatile boolean guardKillLoaded;

    private SnezhnayaExploreSpawnHelper() {}

    public static final class ExplorePoint {
        public final int id;
        public final String kind;
        public final int gadgetId;
        public final int pointType;
        public final Position pos;

        public ExplorePoint(int id, String kind, int gadgetId, int pointType, Position pos) {
            this.id = id;
            this.kind = kind;
            this.gadgetId = gadgetId;
            this.pointType = pointType;
            this.pos = pos;
        }
    }

    private static void ensureLoaded() {
        if (points != null) return;
        synchronized (SnezhnayaExploreSpawnHelper.class) {
            if (points != null) return;
            ArrayList<ExplorePoint> list = new ArrayList<ExplorePoint>();
            try {
                if (Files.isRegularFile(POINTS_FILE)) {
                    String raw = Files.readString(POINTS_FILE, StandardCharsets.UTF_8);
                    JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject o = el.getAsJsonObject();
                        list.add(
                                new ExplorePoint(
                                        o.get("id").getAsInt(),
                                        o.has("kind") ? o.get("kind").getAsString() : "chest",
                                        o.get("gadgetId").getAsInt(),
                                        o.has("pointType") ? o.get("pointType").getAsInt() : 0,
                                        new Position(
                                                o.get("x").getAsFloat(),
                                                o.get("y").getAsFloat(),
                                                o.get("z").getAsFloat())));
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().warn("SnezhnayaExploreSpawnHelper load points failed: {}", e.toString());
            }
            points = List.copyOf(list);
            Grasscutter.getLogger().info("SnezhnayaExploreSpawnHelper loaded {} explore points", points.size());
            ensureClaimedLoaded();
        }
    }

    private static void ensureClaimedLoaded() {
        if (claimedLoaded) return;
        synchronized (SnezhnayaExploreSpawnHelper.class) {
            if (claimedLoaded) return;
            try {
                if (Files.isRegularFile(CLAIMED_FILE)) {
                    String raw = Files.readString(CLAIMED_FILE, StandardCharsets.UTF_8);
                    JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                        try {
                            claimed.add(Integer.parseInt(e.getKey()));
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().warn("SnezhnayaExploreSpawnHelper claimed load failed: {}", e.toString());
            }
            claimedLoaded = true;
        }
    }

    private static void saveClaimed() {
        try {
            Files.createDirectories(CLAIMED_FILE.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Integer id : claimed) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(id).append('"').append(':').append(1);
            }
            sb.append('}');
            Files.writeString(CLAIMED_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("SnezhnayaExploreSpawnHelper claimed save failed: {}", e.toString());
        }
    }

    private static void ensureGuardKillLoaded() {
        if (guardKillLoaded) return;
        synchronized (SnezhnayaExploreSpawnHelper.class) {
            if (guardKillLoaded) return;
            try {
                if (Files.isRegularFile(GUARD_KILL_FILE)) {
                    String raw = Files.readString(GUARD_KILL_FILE, StandardCharsets.UTF_8);
                    JsonObject o = JsonParser.parseString(raw).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                        try {
                            guardKilledAtMs.put(Integer.parseInt(e.getKey()), e.getValue().getAsLong());
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().warn("SnezhnayaExploreSpawnHelper guard-kill load failed: {}", e.toString());
            }
            guardKillLoaded = true;
        }
    }

    private static void saveGuardKills() {
        try {
            Files.createDirectories(GUARD_KILL_FILE.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Integer, Long> e : guardKilledAtMs.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append('"').append(':').append(e.getValue());
            }
            sb.append('}');
            Files.writeString(GUARD_KILL_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("SnezhnayaExploreSpawnHelper guard-kill save failed: {}", e.toString());
        }
    }

    private static void markGuardsKilled(int pointId) {
        ensureGuardKillLoaded();
        guardKilledAtMs.put(pointId, System.currentTimeMillis());
        saveGuardKills();
        Grasscutter.getLogger()
                .info("SnezhnayaExploreSpawnHelper guards killed id={}; respawn in 12h", pointId);
    }

    private static boolean isGuardOnCooldown(int pointId) {
        ensureGuardKillLoaded();
        Long killedAt = guardKilledAtMs.get(pointId);
        if (killedAt == null) return false;
        long now = System.currentTimeMillis();
        if (now - killedAt >= GUARD_RESPAWN_MS) {
            guardKilledAtMs.remove(pointId);
            saveGuardKills();
            return false;
        }
        return true;
    }

    public static void markClaimed(EntityGadget gadget) {
        if (gadget == null) return;
        int gid = gadget.getGroupId();
        if (gid < SYNTH_GROUP_BASE || gid >= SYNTH_GROUP_BASE + 100000) return;
        int id = gid - SYNTH_GROUP_BASE;
        ensureClaimedLoaded();
        if (claimed.add(id)) {
            // Chest is one-shot only. Do NOT wipe camp guards - they stay until killed
            // and respawn on their own 12h timer via ensureGuards().
            try {
                Scene scene = gadget.getScene();
                if (scene != null) {
                    removeGroupChestsOnly(scene, gid);
                }
            } catch (Throwable ignored) {
            }
            saveClaimed();
            Grasscutter.getLogger().info("SnezhnayaExploreSpawnHelper claimed id={} (chest only; guards kept)", id);
        }
    }

    /** Remove opened/claimed chest gadgets; leave EntityMonster guards alone. */
    private static void removeGroupChestsOnly(Scene scene, int groupId) {
        for (GameEntity ge : new ArrayList<GameEntity>(scene.getEntities().values())) {
            if (ge.getGroupId() != groupId) continue;
            if (ge instanceof EntityGadget) {
                scene.removeEntity(ge, VisionType.VisionType_VISION_REMOVE);
            }
        }
    }

    public static void ensureNearby(Scene scene) {
        if (scene == null || scene.getId() != 3) return;
        if (scene.getPlayers() == null || scene.getPlayers().isEmpty()) return;
        ensureLoaded();
        if (points == null || points.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Player player : scene.getPlayers()) {
            if (player == null || player.getPosition() == null) continue;
            Long last = lastCheckMs.get(player.getUid());
            if (last != null && now - last < CHECK_INTERVAL_MS) continue;
            lastCheckMs.put(player.getUid(), now);

            Position ppos = player.getPosition();
            // Only bother in Snezhnaya open-world band
            if (!inSnezhnayaBand(ppos)) continue;

            for (ExplorePoint ep : points) {
                try {
                    double d = ppos.computeDistance(ep.pos);
                    if (d > LEAVE_DIST) {
                        continue;
                    }
                    if (d > NEAR_DIST) continue;

                    boolean chestClaimed = claimed.contains(ep.id);
                    if (chestClaimed) {
                        // Chest already looted once - still refresh/maintain guard camp.
                        if (wantsGuards(ep)) {
                            held.add(ep.id);
                            ensureGuards(scene, ep, player);
                        }
                        continue;
                    }

                    if (hasAlive(scene, ep)) {
                        held.add(ep.id);
                        ensureGuards(scene, ep, player);
                        continue;
                    }
                    spawnOne(scene, ep, player);
                } catch (Throwable t) {
                    Grasscutter.getLogger().debug("SnezhnayaExploreSpawnHelper spawn fail id {}: {}", ep.id, t.toString());
                }
            }
        }

        // Despawn far held entities (chest + guards share synth group)
        try {
            Set<Integer> farGroups = ConcurrentHashMap.newKeySet();
            for (GameEntity ge : new ArrayList<GameEntity>(scene.getEntities().values())) {
                int gid = ge.getGroupId();
                if (gid < SYNTH_GROUP_BASE || gid >= SYNTH_GROUP_BASE + 100000) continue;
                boolean nearAny = false;
                for (Player player : scene.getPlayers()) {
                    if (player == null || player.getPosition() == null) continue;
                    if (player.getPosition().computeDistance(ge.getPosition()) <= LEAVE_DIST) {
                        nearAny = true;
                        break;
                    }
                }
                if (!nearAny) {
                    farGroups.add(gid);
                }
            }
            for (Integer gid : farGroups) {
                removeGroupEntities(scene, gid.intValue());
                int id = gid.intValue() - SYNTH_GROUP_BASE;
                held.remove(id);
                // Leaving range is not a kill - clear hold without starting 12h cooldown.
                guardHeld.remove(id);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void removeGroupEntities(Scene scene, int groupId) {
        for (GameEntity ge : new ArrayList<GameEntity>(scene.getEntities().values())) {
            if (ge.getGroupId() == groupId) {
                scene.removeEntity(ge, VisionType.VisionType_VISION_REMOVE);
            }
        }
    }

    private static boolean inSnezhnayaBand(Position pos) {
        if (pos == null) return false;
        float x = pos.getX();
        float z = pos.getZ();
        return x >= REGION_MIN_X && x <= REGION_MAX_X && z >= REGION_MIN_Z && z <= REGION_MAX_Z;
    }

    private static boolean isExcludedGuardSpot(ExplorePoint ep) {
        // Keep guards for all in-band Snezhnaya chests.
        return ep == null || ep.pos == null || !inSnezhnayaBand(ep.pos);
    }

    private static boolean wantsGuards(ExplorePoint ep) {
        if (ep == null || ep.kind == null) return false;
        switch (ep.kind) {
            case "chest_common":
            case "chest_exquisite":
            case "chest_precious":
            case "chest_luxurious":
            case "chest_remarkable":
                return true;
            default:
                return false;
        }
    }

    private static boolean hasCorrectGuards(Scene scene, ExplorePoint ep) {
        int[] want = guardMonsterIds(ep);
        if (want.length == 0) return true;
        java.util.HashSet<Integer> wantSet = new java.util.HashSet<Integer>();
        for (int id : want) wantSet.add(id);
        int wantGroup = SYNTH_GROUP_BASE + ep.id;
        int matched = 0;
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster)) continue;
            EntityMonster em = (EntityMonster) ge;
            if (em.getGroupId() != wantGroup || !em.isAlive()) continue;
            try {
                if (em.getMonsterData() != null && wantSet.contains(em.getMonsterData().getId())) {
                    matched++;
                }
            } catch (Throwable ignored) {
            }
        }
        return matched >= want.length;
    }

    private static int countAliveGuards(Scene scene, ExplorePoint ep) {
        int wantGroup = SYNTH_GROUP_BASE + ep.id;
        int n = 0;
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster)) continue;
            EntityMonster em = (EntityMonster) ge;
            if (em.getGroupId() == wantGroup && em.isAlive()) n++;
        }
        return n;
    }

    private static void removeGroupMonsters(Scene scene, int groupId) {
        for (GameEntity ge : new ArrayList<GameEntity>(scene.getEntities().values())) {
            if (ge instanceof EntityMonster && ge.getGroupId() == groupId) {
                scene.removeEntity(ge);
            }
        }
    }

    private static int resolveMonsterLevel(Scene scene) {
        int level = 36;
        try {
            if (scene.getWorld() != null) {
                WorldLevelData wld = GameData.getWorldLevelDataMap().get(scene.getWorld().getWorldLevel());
                if (wld != null) {
                    level = Math.max(level, wld.getMonsterLevel());
                }
            }
        } catch (Throwable ignored) {
        }
        return level;
    }

    private static int[] guardMonsterIds(ExplorePoint ep) {
        if ("chest_common".equals(ep.kind)) {
            return pickFromPool(COMMON_GUARD_POOL, ep.id, 3);
        }
        if ("chest_exquisite".equals(ep.kind)
                || "chest_precious".equals(ep.kind)
                || "chest_remarkable".equals(ep.kind)) {
            if (isExcludedGuardSpot(ep)) return new int[0];
            return pickFromPool(ELITE_GUARD_POOL, ep.id, 1);
        }
        if ("chest_luxurious".equals(ep.kind)) {
            return pickFromPool(LUX_GUARD_POOL, ep.id, 1);
        }
        return new int[0];
    }

    /** Deterministic pick of n IDs from pool (same chest gives the same set). */
    private static int[] pickFromPool(int[] poolSrc, int pointId, int n) {
        ArrayList<Integer> pool = new ArrayList<Integer>(poolSrc.length);
        for (int id : poolSrc) {
            pool.add(id);
        }
        Collections.shuffle(pool, new Random(pointId * 31L + 17L));
        int take = Math.min(n, pool.size());
        int[] out = new int[take];
        for (int i = 0; i < take; i++) {
            out[i] = pool.get(i);
        }
        return out;
    }

    private static float[][] guardOffsets(int count) {
        // Ring around chest; supports up to 6.
        float[][] ring =
                new float[][] {
                    {2.8f, 0.0f, 1.2f},
                    {-2.6f, 0.0f, 1.8f},
                    {0.2f, 0.0f, -3.0f},
                    {3.2f, 0.0f, -1.4f},
                    {-3.0f, 0.0f, -1.6f},
                    {0.0f, 0.0f, 3.2f}
                };
        if (count >= ring.length) return ring;
        float[][] out = new float[count][];
        System.arraycopy(ring, 0, out, 0, count);
        return out;
    }

    private static void ensureGuards(Scene scene, ExplorePoint ep, Player player) {
        if (!wantsGuards(ep)) return;
        int[] mids = guardMonsterIds(ep);
        if (mids.length == 0) {
            // Excluded spot: clear leftover camps, leave chest alone.
            removeGroupMonsters(scene, SYNTH_GROUP_BASE + ep.id);
            return;
        }
        if (hasCorrectGuards(scene, ep)) {
            guardHeld.add(ep.id);
            return;
        }
        // Still some alive - do not refill mid-fight / mid-camp.
        if (countAliveGuards(scene, ep) > 0) {
            guardHeld.add(ep.id);
            return;
        }
        // Camp wiped while we were holding it - start the 12h timer.
        if (guardHeld.remove(ep.id)) {
            markGuardsKilled(ep.id);
        }
        if (isGuardOnCooldown(ep.id)) {
            return;
        }
        int groupId = SYNTH_GROUP_BASE + ep.id;
        removeGroupMonsters(scene, groupId);
        int level = resolveMonsterLevel(scene);
        float[][] offsets = guardOffsets(mids.length);
        int spawned = 0;
        for (int i = 0; i < mids.length; i++) {
            MonsterData data = GameData.getMonsterDataMap().get(mids[i]);
            if (data == null) {
                Grasscutter.getLogger().warn("SnezhnayaExploreSpawnHelper missing monster {}", mids[i]);
                continue;
            }
            float[] off = offsets[Math.min(i, offsets.length - 1)];
            Position pos =
                    new Position(
                            ep.pos.getX() + off[0], ep.pos.getY() + off[1], ep.pos.getZ() + off[2]);
            Position rot = new Position(0.0f, (float) (i * 90), 0.0f);
            try {
                EntityMonster em = new EntityMonster(scene, data, pos, rot, level);
                em.setGroupId(groupId);
                em.setConfigId(ep.id * 10 + i + 1);
                em.setPoseId(201);
                em.setAiId(10001);
                // Required for Scene.killEntity drop path (metaMonster != null).
                emu.grasscutter.scripts.data.SceneMonster meta =
                        new emu.grasscutter.scripts.data.SceneMonster();
                meta.config_id = ep.id * 10 + i + 1;
                meta.monster_id = mids[i];
                meta.level = level;
                meta.drop_id = data.getKillDropId();
                meta.pose_id = 201;
                em.setMetaMonster(meta);
                scene.addEntity(em);
                scene.broadcastPacket(new PacketMonsterForceAlertNotify(em.getId()));
                if (player != null && em.getPlayerOnBattle() != null && !em.getPlayerOnBattle().contains(player)) {
                    em.getPlayerOnBattle().add(player);
                }
                spawned++;
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .warn("SnezhnayaExploreSpawnHelper guard spawn fail id={} mid={}: {}", ep.id, mids[i], t.toString());
            }
        }
        if (spawned > 0) {
            guardHeld.add(ep.id);
            Grasscutter.getLogger()
                    .info(
                            "SnezhnayaExploreSpawnHelper guards id={} kind={} n={} lv={}",
                            ep.id,
                            ep.kind,
                            spawned,
                            level);
        }
    }

    private static boolean hasAlive(Scene scene, ExplorePoint ep) {
        int wantGroup = SYNTH_GROUP_BASE + ep.id;
        int wantGadget = resolveGadgetId(ep.gadgetId);
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityGadget)) continue;
            EntityGadget eg = (EntityGadget) ge;
            if (eg.getGroupId() == wantGroup
                    && (eg.getGadgetId() == wantGadget || eg.getGadgetId() == ep.gadgetId)) {
                return true;
            }
        }
        return false;
    }

    /** Map older Default_Lv chest ids (no TextMap interact name) to world chests with names. */
    private static int resolveGadgetId(int gadgetId) {
        switch (gadgetId) {
            case 70210011:
                return 70211101; // common chest
            case 70210021:
                return 70211111; // exquisite chest
            case 70210031:
                return 70211121; // precious chest
            case 70210041:
            case 70210051:
                return 70211131; // luxurious chest
            default:
                return gadgetId;
        }
    }

    /*
     * The escaped literals in the drop-tag methods below are keys into data/ChestDrop.json, whose
     * index values are Chinese (215 of them). They are data identifiers, not display text -
     * translating them would make DropSystem.queryDropData miss and silently drop no loot at all.
     * Written as escapes so this source stays pure ASCII while the keys remain byte-identical.
     */
    /** Region-aware drop tags so WorldChestLootHelper grants the Snezhnaya cryo sigil. */
    private static String dropTagForKind(String kind) {
        if (kind == null) return "\u89e3\u8c1c\u4f4e\u7ea7\u81f3\u51ac";
        switch (kind) {
            case "chest_luxurious":
            case "chest_remarkable":
                return "\u89e3\u8c1c\u8d85\u7ea7\u81f3\u51ac";
            case "chest_precious":
                return "\u89e3\u8c1c\u9ad8\u7ea7\u81f3\u51ac";
            case "chest_exquisite":
            case "chest_puzzle":
                return "\u89e3\u8c1c\u4e2d\u7ea7\u81f3\u51ac";
            default:
                return "\u89e3\u8c1c\u4f4e\u7ea7\u81f3\u51ac";
        }
    }

    private static void spawnOne(Scene scene, ExplorePoint ep, Player player) {
        Position pos = ep.pos.clone();
        Position rot = new Position(0.0f, 0.0f, 0.0f);
        int gadgetId = resolveGadgetId(ep.gadgetId);
        EntityGadget gadget = new EntityGadget(scene, gadgetId, pos, rot);
        gadget.setGroupId(SYNTH_GROUP_BASE + ep.id);
        gadget.setConfigId(ep.id);
        if (ep.pointType > 0) {
            gadget.setPointType(ep.pointType);
        }
        // Preserve the source chest type: several types share the same gadget ID.
        if (ep.kind != null && ep.kind.startsWith("chest")) {
            emu.grasscutter.scripts.data.SceneGadget meta = new emu.grasscutter.scripts.data.SceneGadget();
            meta.gadget_id = gadgetId;
            meta.config_id = ep.id;
            meta.chestType = emu.grasscutter.game.entity.gadget.chest.ChestTypeResolver
                    .fromExploreKind(ep.kind);
            meta.chestGrade = emu.grasscutter.game.entity.gadget.chest.ChestTypeResolver
                    .gradeFromExploreKind(ep.kind);
            meta.drop_tag = dropTagForKind(ep.kind);
            meta.rewardRegion = "snezhnaya"; // The point source is Snezhnaya-specific.
            meta.isOneoff = true;
            gadget.setMetaGadget(meta);
        }
        gadget.setInteractEnabled(true);
        gadget.buildContent();
        scene.addEntity(gadget);
        held.add(ep.id);
        ensureGuards(scene, ep, player);
        Grasscutter.getLogger().info(
            "SnezhnayaExploreSpawnHelper spawn id={} kind={} gadget={} at {},{},{}",
            ep.id,
            ep.kind,
            gadgetId,
            (int) pos.getX(),
            (int) pos.getY(),
            (int) pos.getZ());
    }
}
