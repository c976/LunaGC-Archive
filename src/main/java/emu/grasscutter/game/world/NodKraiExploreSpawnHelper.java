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
 * Spawn exploration / specialty markers near players on scene 3:
 * Nod-Krai (lunar oculi / chests), Snezhnaya (cryo oculi / chests), Fontaine/Natlan/Sumeru
 * (hydro/pyro/dendro oculi plus chests from region_explore_points.json), specialty plants.
 */
public final class NodKraiExploreSpawnHelper {
    private static final double NEAR_DIST = 120.0;
    private static final double LEAVE_DIST = 200.0;
    private static final long CHECK_INTERVAL_MS = 2500L;
    private static final int SYNTH_GROUP_BASE = 910700000;
    private static final Path POINTS_FILE = Path.of("data", "nodkrai_explore_points.json");
    private static final Path SNEZHNAYA_POINTS_FILE = Path.of("data", "snezhnaya_explore_points.json");
    private static final Path MATERIALS_FILE = Path.of("data", "specialty_materials_points.json");
    private static final Path REGION_POINTS_FILE = Path.of("data", "region_explore_points.json");
    private static final Path CLAIMED_FILE = Path.of("data", "nodkrai_explore_claimed.json");
    private static final Path GUARD_KILL_FILE = Path.of("data", "nodkrai_guard_respawn.json");
    /** Same as the bounty system: 12h after a camp wipe before guards respawn. */
    private static final long GUARD_RESPAWN_MS = 12L * 60L * 60L * 1000L;

    // Exquisite/precious/remarkable: Nod-Krai handbook monsters (mapped to
    // closest existing ConfigMonster when assets missing).
    // Common/mora chests: bounty commons - Special Detachment (84) and Patrol Craft (85), 5 random per chest.
    private static final int GUARD_TOOTHTRAP = 24090101; // bounty elite - Ruin Drake (74)
    private static final int[] COMMON_GUARD_POOL =
            new int[] {
                // InvestigationMonsterConfig id=84 Fatui Special Detachment
                23070101, 23070102, 23071101, 23071102, 23072101, 23073101, 23073102,
                23074101, 23074102, 23075101, 23076101, 23076102, 23077101, 23077102,
                23078101, 23078102, 23079101, 23079102, 23079201, 23079202, 23079301, 23079302,
                // InvestigationMonsterConfig id=85 Patrol Craft
                23080102, 23080201, 23080301, 23080302, 23080401, 23080402, 23080501,
                23085101, 23085102, 23085201, 23085202, 23085301, 23085302
            };
    // Exquisite/precious/luxurious/puzzle chests: bounty elites 82, 83 and 86, one per chest.
    private static final int[] ELITE_GUARD_POOL =
            new int[] {
                // id=82
                20080101, 20080201, 20080301, 20080401,
                // id=83
                22130001, 22130002, 22130101, 22130102, 22130103, 22130104, 22130201, 22130202,
                22131001,
                // id=86
                26300101, 26300201, 26300301
            };
    // Snezhnaya chest guards: Fatui.
    private static final int[] SNEZH_COMMON_GUARD_POOL =
            new int[] {
                23020101, 23020102, 23021101, 23021102,
                23030101, 23030102, 23040101, 23040102, 23050101
            };
    private static final int[] SNEZH_ELITE_GUARD_POOL =
            new int[] {
                23060101, 23060201, 23060301, 23060401, 23060501,
                25080101, 25080201, 25080301
            };
    private static final int[] SNEZH_LUX_GUARD_POOL =
            new int[] {25080101, 25080201, 25080301, 25080401, 23060501};
    // Sumeru 3.6 chest guards: bounty Hilichurl Rangers (anemo/hydro).
    private static final int[] SUMERU36_GUARD_POOL =
            new int[] {
                21040101, 21040181, 21040182, // Anemo Hilichurl Rogue
                21040201, 21040281, 21040282, 21040291, 21040292 // Hydro Hilichurl Rogue
            };
    // Fontaine chest guards - bounty elites: 56, 57 and 60.
    private static final int[] FONTAINE_ELITE_GUARD_POOL =
            new int[] {
                20051001, 20051101,
                22110101, 22110201, 22110301, 22110402,
                23060101, 23060201
            };
    // Fontaine chest guards - bounty commons: 54 and 55.
    private static final int[] FONTAINE_COMMON_GUARD_POOL =
            new int[] {
                24060101, 24060102, 24065101, 24065102, 24060201, 24060202, 24065201, 24065202,
                24060301, 24060302, 24065301, 24065302, 24060401, 24060402, 24065401, 24065402,
                24060501, 24060502, 24065501, 24065502, 24060701, 24060702, 24065701, 24065702,
                24060801, 24060802, 24065801, 24065802, 24060901, 24060902, 24065901, 24065902,
                24061001, 24061002, 24066001, 24066002, 24061101, 24061102, 24066101, 24066102,
                24061105, 24061106, 24066105, 24066106, 24061201, 24061202, 24066201, 24066202,
                26151001, 26151002, 26151101, 26151102, 26152101, 26152102, 26152201, 26152202,
                26153101, 26153102, 26154101, 26154201, 26154102, 26154202, 26155101, 26155201,
                26155301, 26156101, 26156201, 26157101, 26157201, 26160101, 26160102, 26160201,
                26160202, 26160301, 26160302
            };
    // Natlan chest guards - bounty commons: 70 and 71.
    private static final int[] NATLAN_COMMON_GUARD_POOL =
            new int[] {
                26200101, 26200201, 26210101, 26210201, 26220101, 26220201,
                25500101, 25500102, 25501101, 25501102, 25501201, 25501202, 25501203,
                25502101, 25502102, 25502103, 25502201, 25502202, 25503101, 25503102, 25503103,
                25504101, 25504102, 25505101, 25505102, 25505201, 25505202,
                25510101, 25510201, 25510301, 25510401, 25510501, 25510601
            };
    // Natlan chest guards - bounty elites: greater spirits, lava statues, ruin drakes and similar.
    private static final int[] NATLAN_ELITE_GUARD_POOL =
            new int[] {
                26190201, 26140201,
                26260101, 26260201, 26260301,
                26270101, 26270201,
                24090101, 24090601,
                22121001, 22121101, 22121201, 22120301, 22120401, 22120501,
                22120601, 22120701, 22120801, 22120901, 22121301, 22121401, 22121501,
                26290101,
                24110101, 24110111
            };

    /** SW Hiisi open-sea map cursor - do not attach the three new camps here. */
    private static final float EXCLUDE_X = 1650.0f;
    private static final float EXCLUDE_Z = 9100.0f;
    private static final double EXCLUDE_RADIUS = 450.0;

    /** Snezhnaya open-world band (ids >= 10000 in snezhnaya_explore_points.json). */
    private static final float SNEZH_MIN_X = 7000.0f;
    private static final float SNEZH_MAX_X = 11000.0f;
    private static final float SNEZH_MIN_Z = 5200.0f;
    private static final float SNEZH_MAX_Z = 8800.0f;

    private static volatile List<ExplorePoint> points;
    private static final ConcurrentHashMap<Integer, Long> lastCheckMs = new ConcurrentHashMap<Integer, Long>();
    private static final Set<Integer> claimed = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> held = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> guardHeld = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<Integer, Long> guardKilledAtMs = new ConcurrentHashMap<Integer, Long>();
    private static volatile boolean claimedLoaded;
    private static volatile boolean guardKillLoaded;

    private NodKraiExploreSpawnHelper() {}

    public static final class ExplorePoint {
        public final int id;
        public final String kind;
        public final int gadgetId;
        public final int pointType;
        public final int itemId;
        public final String sourceRegion;
        public final Position pos;

        public ExplorePoint(int id, String kind, int gadgetId, int pointType, Position pos) {
            this(id, kind, gadgetId, pointType, 0, pos);
        }

        public ExplorePoint(int id, String kind, int gadgetId, int pointType, int itemId, Position pos) {
            this(id, kind, gadgetId, pointType, itemId, null, pos);
        }

        public ExplorePoint(
                int id, String kind, int gadgetId, int pointType, int itemId,
                String sourceRegion, Position pos) {
            this.id = id;
            this.kind = kind;
            this.gadgetId = gadgetId;
            this.pointType = pointType;
            this.itemId = itemId;
            this.sourceRegion = sourceRegion;
            this.pos = pos;
        }
    }

    private static void ensureLoaded() {
        if (points != null) return;
        synchronized (NodKraiExploreSpawnHelper.class) {
            if (points != null) return;
            ArrayList<ExplorePoint> list = new ArrayList<ExplorePoint>();
            loadPointsFile(POINTS_FILE, list);
            loadPointsFile(SNEZHNAYA_POINTS_FILE, list);
            loadPointsFile(MATERIALS_FILE, list);
            loadPointsFile(REGION_POINTS_FILE, list);
            points = List.copyOf(list);
            Grasscutter.getLogger().info("NodKraiExploreSpawnHelper loaded {} explore points", points.size());
            ensureClaimedLoaded();
        }
    }

    private static void loadPointsFile(Path file, ArrayList<ExplorePoint> list) {
        try {
            if (!Files.isRegularFile(file)) return;
            String raw = Files.readString(file, StandardCharsets.UTF_8);
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
                                o.has("itemId") ? o.get("itemId").getAsInt() : 0,
                                o.has("region") && !o.get("region").isJsonNull()
                                        ? o.get("region").getAsString()
                                        : file.equals(POINTS_FILE) ? "nodkrai"
                                        : file.equals(SNEZHNAYA_POINTS_FILE) ? "snezhnaya" : null,
                                new Position(
                                        o.get("x").getAsFloat(),
                                        o.get("y").getAsFloat(),
                                        o.get("z").getAsFloat())));
            }
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("NodKraiExploreSpawnHelper load {} failed: {}", file, e.toString());
        }
    }

    private static void ensureClaimedLoaded() {
        if (claimedLoaded) return;
        synchronized (NodKraiExploreSpawnHelper.class) {
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
                Grasscutter.getLogger().warn("NodKraiExploreSpawnHelper claimed load failed: {}", e.toString());
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
            Grasscutter.getLogger().warn("NodKraiExploreSpawnHelper claimed save failed: {}", e.toString());
        }
    }

    private static void ensureGuardKillLoaded() {
        if (guardKillLoaded) return;
        synchronized (NodKraiExploreSpawnHelper.class) {
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
                Grasscutter.getLogger().warn("NodKraiExploreSpawnHelper guard-kill load failed: {}", e.toString());
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
            Grasscutter.getLogger().warn("NodKraiExploreSpawnHelper guard-kill save failed: {}", e.toString());
        }
    }

    private static void markGuardsKilled(int pointId) {
        ensureGuardKillLoaded();
        guardKilledAtMs.put(pointId, System.currentTimeMillis());
        saveGuardKills();
        Grasscutter.getLogger()
                .info("NodKraiExploreSpawnHelper guards killed id={}; respawn in 12h", pointId);
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
            Grasscutter.getLogger().info("NodKraiExploreSpawnHelper claimed id={} (chest only; guards kept)", id);
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
            // Nod-Krai (north) or Snezhnaya band
            if (!inExploreBand(ppos)) continue;

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
                    Grasscutter.getLogger().debug("NodKraiExploreSpawnHelper spawn fail id {}: {}", ep.id, t.toString());
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
                // VISION_REMOVE: opened chests vanish for the client (VISION_DIE leaves them).
                scene.removeEntity(ge, VisionType.VisionType_VISION_REMOVE);
            }
        }
    }

    private static boolean inSnezhnayaBand(Position pos) {
        if (pos == null) return false;
        float x = pos.getX();
        float z = pos.getZ();
        return x >= SNEZH_MIN_X && x <= SNEZH_MAX_X && z >= SNEZH_MIN_Z && z <= SNEZH_MAX_Z;
    }

    private static boolean inExploreBand(Position pos) {
        if (pos == null) return false;
        if (pos.getZ() >= 8800.0f) return true; // Nod-Krai
        if (inSnezhnayaBand(pos)) return true;
        // Fontaine (incl. 4.0-4.6 Remuria / Nostoi)
        if (pos.getZ() >= 2700.0f
                && pos.getZ() <= 5600.0f
                && pos.getX() >= 1000.0f
                && pos.getX() <= 5200.0f) {
            return true;
        }
        // Natlan
        if (pos.getZ() >= 6000.0f
                && pos.getZ() <= 11000.0f
                && pos.getX() >= -4000.0f
                && pos.getX() <= 2000.0f) {
            return true;
        }
        // Sumeru 3.6 (Girdle of the Sands / desert north)
        if (pos.getZ() >= 5200.0f
                && pos.getZ() <= 7200.0f
                && pos.getX() >= -500.0f
                && pos.getX() <= 1500.0f) {
            return true;
        }
        return false;
    }

    /** Snezhnaya explore ids are 10001-10486 (not Fontaine/Natlan/Sumeru36). */
    private static boolean isSnezhnayaPoint(ExplorePoint ep) {
        return ep != null && ep.id >= 10000 && ep.id < 20000;
    }

    /** region_explore_points.json Fontaine chests / hydro oculi (20000-29999). */
    private static boolean isFontainePoint(ExplorePoint ep) {
        return ep != null && ep.id >= 20000 && ep.id < 30000;
    }

    /** region_explore_points.json Natlan chests / pyro oculi (30000-39999). */
    private static boolean isNatlanPoint(ExplorePoint ep) {
        return ep != null && ep.id >= 30000 && ep.id < 40000;
    }

    /** region_explore_points.json Sumeru 3.6 chests / dendro oculi (40000+). */
    private static boolean isSumeru36Point(ExplorePoint ep) {
        return ep != null && ep.id >= 40000 && ep.id < 50000;
    }

    /** Fontaine 20000+ / Natlan 30000+ / Sumeru36 40000+ imported markers. */
    private static boolean isImportedRegionPoint(ExplorePoint ep) {
        return ep != null && ep.id >= 20000;
    }

    private static boolean isExcludedGuardSpot(ExplorePoint ep) {
        if (ep == null || ep.pos == null) return true;
        if (isFontainePoint(ep) || isNatlanPoint(ep) || isSumeru36Point(ep)) {
            return false;
        }
        if (isImportedRegionPoint(ep)) {
            return true;
        }
        if (isSnezhnayaPoint(ep)) {
            return !inSnezhnayaBand(ep.pos);
        }
        // junk / out-of-band Nod-Krai points
        if (ep.pos.getZ() < 8000.0f) return true;
        double dx = ep.pos.getX() - EXCLUDE_X;
        double dz = ep.pos.getZ() - EXCLUDE_Z;
        return Math.hypot(dx, dz) <= EXCLUDE_RADIUS;
    }

    private static boolean wantsGuards(ExplorePoint ep) {
        if (ep == null || ep.kind == null) return false;
        switch (ep.kind) {
            case "chest_common":
            case "chest_mora":
            case "chest_exquisite":
            case "chest_precious":
            case "chest_luxurious":
            case "chest_remarkable":
            case "chest_puzzle":
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
        if (isFontainePoint(ep)) {
            // Common/mora: 3-5 clockwork and primordial beasts. Exquisite and above: one elite.
            if ("chest_common".equals(ep.kind) || "chest_mora".equals(ep.kind)) {
                int n = 3 + Math.floorMod(ep.id, 3); // 3/4/5
                return pickFromPool(FONTAINE_COMMON_GUARD_POOL, ep.id, n);
            }
            if ("chest_exquisite".equals(ep.kind)
                    || "chest_precious".equals(ep.kind)
                    || "chest_luxurious".equals(ep.kind)
                    || "chest_remarkable".equals(ep.kind)
                    || "chest_puzzle".equals(ep.kind)) {
                return pickFromPool(FONTAINE_ELITE_GUARD_POOL, ep.id, 1);
            }
            return new int[0];
        }
        if (isNatlanPoint(ep)) {
            // Common/mora: 5-6 saurians and tribal warriors. Exquisite and above: one elite, seeded per point.
            if ("chest_common".equals(ep.kind) || "chest_mora".equals(ep.kind)) {
                int n = 5 + Math.floorMod(ep.id, 2); // 5/6
                return pickFromPool(NATLAN_COMMON_GUARD_POOL, ep.id, n);
            }
            if ("chest_exquisite".equals(ep.kind)
                    || "chest_precious".equals(ep.kind)
                    || "chest_luxurious".equals(ep.kind)
                    || "chest_remarkable".equals(ep.kind)
                    || "chest_puzzle".equals(ep.kind)) {
                return pickFromPool(NATLAN_ELITE_GUARD_POOL, ep.id, 1);
            }
            return new int[0];
        }
        if (isSumeru36Point(ep)) {
            // Sumeru 3.6 mora and exquisite chests: one Hilichurl Ranger each.
            if ("chest_mora".equals(ep.kind)
                    || "chest_common".equals(ep.kind)
                    || "chest_exquisite".equals(ep.kind)
                    || "chest_precious".equals(ep.kind)
                    || "chest_luxurious".equals(ep.kind)
                    || "chest_remarkable".equals(ep.kind)
                    || "chest_puzzle".equals(ep.kind)) {
                return pickFromPool(SUMERU36_GUARD_POOL, ep.id, 1);
            }
            return new int[0];
        }
        if (isSnezhnayaPoint(ep)) {
            if ("chest_common".equals(ep.kind)) {
                return pickFromPool(SNEZH_COMMON_GUARD_POOL, ep.id, 3);
            }
            if ("chest_exquisite".equals(ep.kind)
                    || "chest_precious".equals(ep.kind)
                    || "chest_remarkable".equals(ep.kind)) {
                if (isExcludedGuardSpot(ep)) return new int[0];
                return pickFromPool(SNEZH_ELITE_GUARD_POOL, ep.id, 1);
            }
            if ("chest_luxurious".equals(ep.kind)) {
                return pickFromPool(SNEZH_LUX_GUARD_POOL, ep.id, 1);
            }
            return new int[0];
        }
        // Other imported region markers without a dedicated pool
        if (isImportedRegionPoint(ep)) {
            return new int[0];
        }
        // Common and mora chests: 5 drawn from the detachment and patrol-craft pools, seeded per point.
        if ("chest_common".equals(ep.kind) || "chest_mora".equals(ep.kind)) {
            return pickFromPool(COMMON_GUARD_POOL, ep.id, 5);
        }
        // Exquisite, precious, luxurious and puzzle chests: one from the two elite pools.
        if ("chest_exquisite".equals(ep.kind)
                || "chest_precious".equals(ep.kind)
                || "chest_remarkable".equals(ep.kind)
                || "chest_puzzle".equals(ep.kind)) {
            if (isExcludedGuardSpot(ep)) return new int[0];
            return pickFromPool(ELITE_GUARD_POOL, ep.id, 1);
        }
        // Luxurious: bounty elite Ruin Drake (74).
        if ("chest_luxurious".equals(ep.kind)) {
            return new int[] {GUARD_TOOTHTRAP};
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
                Grasscutter.getLogger().warn("NodKraiExploreSpawnHelper missing monster {}", mids[i]);
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
                        .warn("NodKraiExploreSpawnHelper guard spawn fail id={} mid={}: {}", ep.id, mids[i], t.toString());
            }
        }
        if (spawned > 0) {
            guardHeld.add(ep.id);
            Grasscutter.getLogger()
                    .info(
                            "NodKraiExploreSpawnHelper guards id={} kind={} n={} lv={}",
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
            if (!eg.isAlive()) continue;
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
    /** Region-aware drop tags so WorldChestLootHelper grants the correct region sigil. */
    private static String dropTagForKind(ExplorePoint ep) {
        String region = regionNameForPoint(ep);
        String kind = ep != null ? ep.kind : null;
        if (kind == null) return "\u89e3\u8c1c\u4f4e\u7ea7" + region;
        switch (kind) {
            case "chest_luxurious":
            case "chest_remarkable":
                return "\u89e3\u8c1c\u8d85\u7ea7" + region;
            case "chest_precious":
                return "\u89e3\u8c1c\u9ad8\u7ea7" + region;
            case "chest_exquisite":
            case "chest_puzzle":
                return "\u89e3\u8c1c\u4e2d\u7ea7" + region;
            default:
                return "\u89e3\u8c1c\u4f4e\u7ea7" + region;
        }
    }

    private static String regionNameForPoint(ExplorePoint ep) {
        if (ep == null) return "\u632a\u5fb7\u5361\u83b1";
        if (ep.id >= 40000 && ep.id < 50000) return "\u987b\u5f25";
        if (ep.id >= 30000 && ep.id < 40000) return "\u7eb3\u5854";
        if (ep.id >= 20000 && ep.id < 30000) return "\u67ab\u4e39";
        if (ep.id >= 10000 && ep.id < 20000) return "\u81f3\u51ac";
        return "\u632a\u5fb7\u5361\u83b1";
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
            meta.drop_tag = dropTagForKind(ep);
            meta.rewardRegion = ep.sourceRegion;
            meta.isOneoff = true;
            gadget.setMetaGadget(meta);
        }
        // Specialty materials: force gather item, needed when GatherExcel has no row for it.
        if (ep.itemId > 0) {
            try {
                SpawnDataEntry entry = new SpawnDataEntry();
                java.lang.reflect.Field f = SpawnDataEntry.class.getDeclaredField("gatherItemId");
                f.setAccessible(true);
                f.setInt(entry, ep.itemId);
                java.lang.reflect.Field gf = SpawnDataEntry.class.getDeclaredField("gadgetId");
                gf.setAccessible(true);
                gf.setInt(entry, gadgetId);
                java.lang.reflect.Field cf = SpawnDataEntry.class.getDeclaredField("configId");
                cf.setAccessible(true);
                cf.setInt(entry, ep.id);
                java.lang.reflect.Field pf = SpawnDataEntry.class.getDeclaredField("pos");
                pf.setAccessible(true);
                pf.set(entry, pos);
                gadget.setSpawnEntry(entry);
            } catch (Throwable t) {
                Grasscutter.getLogger()
                        .debug("NodKraiExploreSpawnHelper spawnEntry itemId={} fail: {}", ep.itemId, t.toString());
            }
        }
        gadget.setInteractEnabled(true);
        gadget.buildContent();
        scene.addEntity(gadget);
        held.add(ep.id);
        ensureGuards(scene, ep, player);
        Grasscutter.getLogger().info(
            "NodKraiExploreSpawnHelper spawn id={} kind={} gadget={} at {},{},{}",
            ep.id,
            ep.kind,
            gadgetId,
            (int) pos.getX(),
            (int) pos.getY(),
            (int) pos.getZ());
    }
}
