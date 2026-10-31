package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.SceneMonster;
import emu.grasscutter.scripts.data.SceneSuite;
import emu.grasscutter.server.packet.send.PacketWorktopOptionNotify;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lupus Boreas, Dominator of Wolves:
 * <ul>
 *   <li>Starting the trial runs suite3 for the intro VFX and reminder, spawning the boss about 4 seconds
 *       later so the intro animation is not skipped
 *   <li>After claiming the trounce blossom nothing respawns until the trial is started again
 *   <li>WorldBossSpawnHelper must not auto-spawn for this group
 * </ul>
 */
public final class AndriusTrialStartHelper {
    public static final int GROUP_ID = 133002259;
    public static final int SWORD_CONFIG_ID = 259009;
    public static final int OPERATOR_CONFIG_ID = 2299;
    public static final int START_OPTION_ID = 2902;
    public static final int MONSTER_ID = 29020102;
    public static final int MONSTER_CONFIG_ID = 954;
    public static final float SPAWN_X = 1981.685f;
    public static final float SPAWN_Y = 250.080f;
    public static final float SPAWN_Z = -238.965f;
    public static final int FLOWER_CONFIG_ID = 259005;
    public static final int FLOWER_GADGET_ID = 70210106;
    public static final float FLOWER_X = 1984.880f;
    public static final float FLOWER_Y = 250.190f;
    public static final float FLOWER_Z = -246.872f;
    /** Intro reminder used by lua GADGET_CREATE_343. */
    public static final int INTRO_REMINDER_ID = 30020121;
    /**
     * Combat gadgets created by LupiBoreas abilities (ice ring / missiles / anchors).
     * Many use lifeInfinite=true and survive after the boss model is gone.
     */
    private static final int[] ANDRIUS_COMBAT_GADGET_IDS =
            new int[] {
                42902001, 42902002, 42902003, 42902004, 42902005, 42902007, 42902011, 42902012,
                42902013, 42902014, 42902015, 42902016, 42902017, 42902018, 42902021, 42902022,
                42902023, 42902024, 42902035, 42902041, 42902042, 42902043, 42902046, 42902048,
                42902049, 42902050, 42902054, 42902055, 42902056
            };

    /** sceneId -> intro already scheduled (avoid double suite3). */
    private static final ConcurrentHashMap<Integer, Long> INTRO_UNTIL_MS = new ConcurrentHashMap<>();
    /**
     * After the kill, and while the trounce blossom exists, keep the "start trial" option off for the
     * death animation and claim phase.
     * Cleared in {@link #onFlowerClaimed}.
     */
    private static final ConcurrentHashMap<Integer, Boolean> SUPPRESS_START_OPTIONS =
            new ConcurrentHashMap<>();

    private AndriusTrialStartHelper() {}

    /** WorldBossSpawnHelper must not auto-spawn Andrius (manual trial only). */
    public static boolean shouldBlockAutoSpawn(int groupId) {
        return groupId == GROUP_ID;
    }

    /** Claim helper must not immediate-respawn Andrius after flower. */
    public static boolean shouldSkipClaimRespawn(int groupId) {
        return groupId == GROUP_ID;
    }

    public static boolean isAndriusStartGadget(EntityGadget gadget) {
        if (gadget == null) {
            return false;
        }
        int cfg = gadget.getConfigId();
        return gadget.getGroupId() == GROUP_ID
                && (cfg == SWORD_CONFIG_ID || cfg == OPERATOR_CONFIG_ID);
    }

    public static void onGadgetCreated(EntityGadget gadget) {
        if (!isAndriusStartGadget(gadget)) {
            return;
        }
        Scene scene = gadget.getScene();
        if (scene != null && shouldHideStartOptions(scene)) {
            stripOneStartGadget(gadget);
            return;
        }
        ensureWorktopOption(gadget);
    }

    public static boolean tryStart(Player player, EntityGadget gadget, int optionId) {
        if (player == null || !isAndriusStartGadget(gadget)) {
            return false;
        }
        if (optionId != 0 && optionId != START_OPTION_ID) {
            return false;
        }

        Scene scene = player.getScene();
        if (scene == null) {
            return false;
        }

        stripStartOptions(scene);
        if (emu.grasscutter.game.quest.RazorWolfQuestSupport.isActiveBattle(
                scene.getWorld().getHost().getQuestManager().getMainQuestById(454))) return true;

        if (hasAliveAndrius(scene)) {
            clearAndriusLimbo(scene);
            dedupeAndrius(scene);
            Grasscutter.getLogger()
                    .info(
                            "AndriusTrialStartHelper reuse living boss uid={} cfg={}",
                            player.getUid(),
                            gadget.getConfigId());
            return true;
        }

        if (isIntroPending(scene)) {
            return true;
        }

        boolean started = startOfficialIntro(scene, player);
        Grasscutter.getLogger()
                .info(
                        "AndriusTrialStartHelper intro uid={} cfg={} option={} started={}",
                        player.getUid(),
                        gadget.getConfigId(),
                        optionId,
                        started);
        return true;
    }

    /**
     * After flower claim: clear arena to idle suite4 + worktop options, no boss.
     */
    public static void onFlowerClaimed(Scene scene) {
        if (scene == null) {
            return;
        }
        INTRO_UNTIL_MS.remove(scene.getId());
        SUPPRESS_START_OPTIONS.remove(scene.getId());
        removeLivingAndrius(scene);
        try {
            WorldBossClaimHelper.clearFlowerSuppress(GROUP_ID);
        } catch (Throwable ignored) {
        }
        restoreIdleArena(scene);
        Grasscutter.getLogger().info("AndriusTrialStartHelper flower claimed - idle arena, no auto respawn");
    }

    public static void onBossKilled(EntityMonster monster) {
        if (monster == null) {
            return;
        }
        int mid = monster.getEntityTypeId();
        // The story wolf is removed by MQ454's cutscene cleanup, not a weekly-boss kill.
        if (emu.grasscutter.game.quest.RazorWolfQuestSupport.isStoryWolf(
                monster.getScene() != null ? monster.getScene().getId() : 0,
                monster.getGroupId(), monster.getConfigId(), mid)) return;
        if (mid != MONSTER_ID && mid != 29020101) {
            return;
        }
        Scene scene = monster.getScene();
        if (scene == null) {
            return;
        }
        INTRO_UNTIL_MS.remove(scene.getId());
        // Hide "start trial" through the death animation and flower; only restore after the claim.
        SUPPRESS_START_OPTIONS.put(scene.getId(), Boolean.TRUE);
        try {
            if (monster.getGroupId() <= 0) {
                monster.setGroupId(GROUP_ID);
            }
            if (monster.getConfigId() <= 0) {
                monster.setConfigId(MONSTER_CONFIG_ID);
            }
        } catch (Throwable ignored) {
        }
        try {
            SceneScriptManager sm = scene.getScriptManager();
            if (sm != null) {
                var inst = sm.getGroupInstanceById(GROUP_ID);
                if (inst != null) {
                    inst.getCachedVariables().put("boss_exist", 0);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            WorldBossClaimHelper.clearFlowerSuppress(GROUP_ID);
        } catch (Throwable ignored) {
        }
        try {
            WorldBossSpawnHelper.onWorldBossKilled(monster);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .debug("AndriusTrialStartHelper onWorldBossKilled: {}", t.toString());
        }
        if (!hasTrounceFlower(scene)) {
            spawnFlowerFallback(scene);
        }
        clearArenaAftermath(scene);
        stripStartOptions(scene);
        // Lua / group refresh may re-hang options mid death anim - keep stripping.
        scheduleStripBurst(scene);
        // Late-spawned ice gadgets / delayed ability creates
        scheduleAftermathBurst(scene);
    }

    /** True while boss alive, intro playing, flower out, or post-kill suppress. */
    private static boolean shouldHideStartOptions(Scene scene) {
        if (scene == null) {
            return false;
        }
        if (emu.grasscutter.game.quest.RazorWolfQuestSupport.isActiveBattle(
                scene.getWorld().getHost().getQuestManager().getMainQuestById(454))) return true;
        if (hasAliveAndrius(scene) || isIntroPending(scene)) {
            return true;
        }
        if (Boolean.TRUE.equals(SUPPRESS_START_OPTIONS.get(scene.getId()))) {
            return true;
        }
        return hasTrounceFlower(scene);
    }

    private static void scheduleStripBurst(Scene scene) {
        if (scene == null) {
            return;
        }
        int[] delays = new int[] {1, 2, 3, 5, 8};
        for (int delay : delays) {
            try {
                final int d = delay;
                scene.getScheduler()
                        .scheduleDelayedTask(
                                () -> {
                                    try {
                                        if (shouldHideStartOptions(scene)) {
                                            stripStartOptions(scene);
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                },
                                d);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void scheduleAftermathBurst(Scene scene) {
        if (scene == null) {
            return;
        }
        int[] delays = new int[] {0, 1, 2, 4};
        for (int delay : delays) {
            try {
                final int d = delay;
                if (d == 0) {
                    clearArenaAftermath(scene);
                    continue;
                }
                scene.getScheduler()
                        .scheduleDelayedTask(
                                () -> {
                                    try {
                                        clearArenaAftermath(scene);
                                    } catch (Throwable ignored) {
                                    }
                                },
                                d);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Remove lingering phase-2 ice ring / combat gadgets and turn off arena weather.
     * Boss model death alone does not despawn lifeInfinite IceGadgetArea entities.
     */
    private static void clearArenaAftermath(Scene scene) {
        if (scene == null) {
            return;
        }
        try {
            SceneScriptManager sm = scene.getScriptManager();
            if (sm != null) {
                SceneGroup group = sm.getGroupById(GROUP_ID);
                if (group != null) {
                    SceneSuite suite3 = group.getSuiteByIndex(3);
                    SceneSuite suite5 = group.getSuiteByIndex(5);
                    SceneSuite suite6 = group.getSuiteByIndex(6);
                    if (suite3 != null) {
                        try {
                            sm.removeGroupSuite(group, suite3);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (suite6 != null) {
                        try {
                            sm.removeGroupSuite(group, suite6);
                        } catch (Throwable ignored) {
                        }
                    }
                    // Keep suite5 off while the flower or suppression is active - avoids "start trial" on death.
                    if (suite5 != null && shouldHideStartOptions(scene)) {
                        try {
                            sm.removeGroupSuite(group, suite5);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        ArrayList<GameEntity> remove = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityGadget eg)) {
                continue;
            }
            int gid = eg.getGadgetId();
            int cfg = eg.getConfigId();
            // Keep sword / flower / entrance worktop.
            if (cfg == SWORD_CONFIG_ID
                    || cfg == FLOWER_CONFIG_ID
                    || cfg == OPERATOR_CONFIG_ID
                    || gid == FLOWER_GADGET_ID) {
                continue;
            }
            if (cfg == 2222 || cfg == 2223 || cfg == 2238 || isAndriusCombatGadgetId(gid)) {
                remove.add(eg);
            }
        }
        if (!remove.isEmpty()) {
            try {
                scene.removeEntities(remove, VisionType.VisionType_VISION_REMOVE);
            } catch (Throwable t) {
                for (GameEntity ge : remove) {
                    try {
                        scene.removeEntity(ge, VisionType.VisionType_VISION_REMOVE);
                    } catch (Throwable ignored) {
                        try {
                            scene.removeEntity(ge);
                        } catch (Throwable ignored2) {
                        }
                    }
                }
            }
            Grasscutter.getLogger()
                    .info("AndriusTrialStartHelper cleared {} aftermath gadgets", remove.size());
        }

        // Official lua: SetWeatherAreaState(4, 0) on kill - clear ESP_Monster_LupiBoreas weather.
        try {
            for (Player p : scene.getPlayers()) {
                try {
                    if (p.getWeatherId() == 4) {
                        p.setWeather(1, emu.grasscutter.game.props.ClimateType.CLIMATE_SUNNY);
                    }
                } catch (Throwable ignored) {
                    try {
                        p.setWeather(0);
                    } catch (Throwable ignored2) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isAndriusCombatGadgetId(int gadgetId) {
        for (int id : ANDRIUS_COMBAT_GADGET_IDS) {
            if (id == gadgetId) {
                return true;
            }
        }
        // Broad catch for other LupiBoreas 42902xxx combat props near the arena.
        return gadgetId >= 42902001 && gadgetId <= 42902060;
    }

    private static boolean isIntroPending(Scene scene) {
        Long until = INTRO_UNTIL_MS.get(scene.getId());
        return until != null && System.currentTimeMillis() < until;
    }

    private static boolean startOfficialIntro(Scene scene, Player player) {
        removeLivingAndrius(scene);
        // Intro gadgets + 4s CreateMonster axis; keep pending a bit past that.
        INTRO_UNTIL_MS.put(scene.getId(), System.currentTimeMillis() + 10000L);

        boolean suiteOk = false;
        try {
            SceneScriptManager sm = scene.getScriptManager();
            if (sm != null) {
                SceneGroup group = sm.getGroupById(GROUP_ID);
                var inst = sm.getGroupInstanceById(GROUP_ID);
                if (group != null && inst != null) {
                    SceneSuite suite3 = group.getSuiteByIndex(3);
                    SceneSuite suite5 = group.getSuiteByIndex(5);
                    // Drop stale cutscene gadgets so 2222 fires GADGET_CREATE again
                    // (reminder + InitTimeAxis CreateMonster @ 4s).
                    if (suite3 != null) {
                        try {
                            sm.removeGroupSuite(group, suite3);
                        } catch (Throwable ignored) {
                        }
                        sm.addGroupSuite(inst, suite3);
                        suiteOk = true;
                    }
                    // Mirror lua SELECT_OPTION: kill entrance operator suite.
                    if (suite5 != null) {
                        try {
                            sm.killGroupSuite(group, suite5);
                        } catch (Throwable ignored) {
                            try {
                                sm.removeGroupSuite(group, suite5);
                            } catch (Throwable ignored2) {
                            }
                        }
                    }
                    try {
                        inst.getCachedVariables().put("boss_exist", 1);
                        inst.getCachedVariables().put("boss_killself", 0);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("AndriusTrialStartHelper suite3 failed: {}", t.toString());
        }

        // Do NOT broadcast Reminder here - gadget 2222 create already does ShowReminder.
        // Early Reminder + Instant TimeAxis spawn was clipping the camera into the floor.

        // Fallback if TIME_AXIS / CreateMonster miss; official spawn is at ~4s.
        final int uid = player.getUid();
        try {
            scene.getScheduler()
                    .scheduleDelayedTask(
                            () -> {
                                try {
                                    if (scene.getPlayers().isEmpty()) {
                                        return;
                                    }
                                    if (hasAliveAndrius(scene)) {
                                        clearAndriusLimbo(scene);
                                        dedupeAndrius(scene);
                                        return;
                                    }
                                    Player host =
                                            scene.getPlayers().stream()
                                                    .filter(p -> p.getUid() == uid)
                                                    .findFirst()
                                                    .orElse(
                                                            scene.getPlayers().isEmpty()
                                                                    ? null
                                                                    : scene.getPlayers().iterator().next());
                                    spawnAndriusFromGroupMeta(scene);
                                    if (!hasAliveAndrius(scene)) {
                                        spawnAndriusNow(scene, host);
                                    }
                                    clearAndriusLimbo(scene);
                                    dedupeAndrius(scene);
                                    stripStartOptions(scene);
                                } catch (Throwable t) {
                                    Grasscutter.getLogger()
                                            .warn(
                                                    "AndriusTrialStartHelper delayed spawn failed: {}",
                                                    t.toString());
                                } finally {
                                    INTRO_UNTIL_MS.remove(scene.getId());
                                }
                            },
                            6);
        } catch (Throwable t) {
            spawnAndriusFromGroupMeta(scene);
            if (!hasAliveAndrius(scene)) {
                spawnAndriusNow(scene, player);
            }
            clearAndriusLimbo(scene);
            INTRO_UNTIL_MS.remove(scene.getId());
            return suiteOk;
        }
        return suiteOk;
    }

    private static void restoreIdleArena(Scene scene) {
        try {
            SceneScriptManager sm = scene.getScriptManager();
            if (sm != null) {
                var inst = sm.getGroupInstanceById(GROUP_ID);
                if (inst != null) {
                    sm.refreshGroup(inst, 4, false);
                    try {
                        inst.getCachedVariables().put("boss_exist", 1);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .debug("AndriusTrialStartHelper restoreIdleArena: {}", t.toString());
        }
        // Re-attach start options after refresh (GROUP_REFRESH may re-add suite5).
        try {
            scene.getScheduler()
                    .scheduleDelayedTask(
                            () -> {
                                try {
                                    if (shouldHideStartOptions(scene)) {
                                        stripStartOptions(scene);
                                        return;
                                    }
                                    for (GameEntity ge : scene.getEntities().values()) {
                                        if (ge instanceof EntityGadget eg && isAndriusStartGadget(eg)) {
                                            ensureWorktopOption(eg);
                                        }
                                    }
                                } catch (Throwable ignored) {
                                }
                            },
                            1);
        } catch (Throwable ignored) {
            if (!shouldHideStartOptions(scene)) {
                for (GameEntity ge : scene.getEntities().values()) {
                    if (ge instanceof EntityGadget eg && isAndriusStartGadget(eg)) {
                        ensureWorktopOption(eg);
                    }
                }
            } else {
                stripStartOptions(scene);
            }
        }
    }

    private static void ensureWorktopOption(EntityGadget gadget) {
        GadgetWorktop worktop;
        if (gadget.getContent() instanceof GadgetWorktop existing) {
            worktop = existing;
        } else {
            gadget.replaceContent(new GadgetWorktop(gadget));
            if (!(gadget.getContent() instanceof GadgetWorktop created)) {
                return;
            }
            worktop = created;
        }
        worktop.addWorktopOptions(new int[] {START_OPTION_ID});
        try {
            Scene scene = gadget.getScene();
            if (scene != null) {
                scene.broadcastPacket(new PacketWorktopOptionNotify(gadget));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void stripStartOptions(Scene scene) {
        if (scene == null) {
            return;
        }
        for (GameEntity ge : scene.getEntities().values()) {
            if (ge instanceof EntityGadget eg && isAndriusStartGadget(eg)) {
                stripOneStartGadget(eg);
            }
        }
    }

    private static void stripOneStartGadget(EntityGadget eg) {
        try {
            if (eg.getContent() instanceof GadgetWorktop worktop) {
                worktop.removeWorktopOption(START_OPTION_ID);
                worktop.getWorktopOptions().clear();
                Scene scene = eg.getScene();
                if (scene != null) {
                    scene.broadcastPacket(new PacketWorktopOptionNotify(eg));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasAliveAndrius(Scene scene) {
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster em) || !em.isAlive()) {
                continue;
            }
            if (em.getEntityTypeId() == MONSTER_ID || em.getEntityTypeId() == 29020101) {
                return true;
            }
        }
        return false;
    }

    private static void removeLivingAndrius(Scene scene) {
        ArrayList<GameEntity> remove = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster em)) {
                continue;
            }
            if (em.getGroupId() == GROUP_ID
                    || em.getEntityTypeId() == MONSTER_ID
                    || em.getEntityTypeId() == 29020101) {
                remove.add(em);
            }
        }
        for (GameEntity ge : remove) {
            try {
                scene.removeEntity(ge, VisionType.VisionType_VISION_REMOVE);
            } catch (Throwable ignored) {
                try {
                    scene.removeEntity(ge);
                } catch (Throwable ignored2) {
                }
            }
        }
    }

    private static void dedupeAndrius(Scene scene) {
        EntityMonster keep = null;
        ArrayList<EntityMonster> extras = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster em) || !em.isAlive()) {
                continue;
            }
            if (em.getEntityTypeId() != MONSTER_ID && em.getEntityTypeId() != 29020101) {
                continue;
            }
            if (keep == null) {
                keep = em;
            } else {
                extras.add(em);
            }
        }
        for (EntityMonster em : extras) {
            try {
                scene.removeEntity(em);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void clearAndriusLimbo(Scene scene) {
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityMonster em)) {
                continue;
            }
            if (em.getEntityTypeId() != MONSTER_ID && em.getEntityTypeId() != 29020101) {
                continue;
            }
            forceClearLimbo(em);
        }
    }

    private static void forceClearLimbo(GameEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            entity.setLockHP(false);
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m =
                    emu.grasscutter.game.entity.GameEntity.class.getMethod("clearLimbo");
            m.invoke(entity);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> c = emu.grasscutter.game.entity.GameEntity.class;
            java.lang.reflect.Field limbo = c.getDeclaredField("limbo");
            limbo.setAccessible(true);
            limbo.setBoolean(entity, false);
            java.lang.reflect.Field thr = c.getDeclaredField("limboHpThreshold");
            thr.setAccessible(true);
            thr.setFloat(entity, 0f);
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasTrounceFlower(Scene scene) {
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityGadget eg)) {
                continue;
            }
            try {
                if (eg.getGroupId() == GROUP_ID
                        && (eg.getGadgetId() == FLOWER_GADGET_ID
                                || eg.getConfigId() == FLOWER_CONFIG_ID)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void spawnFlowerFallback(Scene scene) {
        try {
            var sm = scene.getScriptManager();
            if (sm != null) {
                var group = sm.getGroupById(GROUP_ID);
                if (group != null && group.gadgets != null) {
                    var meta = group.gadgets.get(FLOWER_CONFIG_ID);
                    if (meta != null) {
                        EntityGadget flower =
                                new EntityGadget(
                                        scene,
                                        meta.gadget_id > 0 ? meta.gadget_id : FLOWER_GADGET_ID,
                                        meta.pos);
                        flower.setGroupId(GROUP_ID);
                        flower.setConfigId(FLOWER_CONFIG_ID);
                        flower.setBlockId(group.block_id);
                        if (meta.rot != null) {
                            flower.getRotation().set(meta.rot);
                        }
                        flower.setMetaGadget(meta);
                        flower.buildContent();
                        scene.addEntity(flower);
                        Grasscutter.getLogger()
                                .info("AndriusTrialStartHelper spawned trounce flower from group meta");
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Position pos = new Position(FLOWER_X, FLOWER_Y, FLOWER_Z);
            EntityGadget flower = new EntityGadget(scene, FLOWER_GADGET_ID, pos);
            flower.setGroupId(GROUP_ID);
            flower.setConfigId(FLOWER_CONFIG_ID);
            emu.grasscutter.scripts.data.SceneGadget meta =
                    new emu.grasscutter.scripts.data.SceneGadget();
            meta.config_id = FLOWER_CONFIG_ID;
            meta.gadget_id = FLOWER_GADGET_ID;
            meta.pos = pos.clone();
            // Drop tag keyed against the game data, which is Chinese; escaped to keep this file ASCII.
            meta.drop_tag = "\u5317\u98ce\u72fc";
            meta.boss_chest = new emu.grasscutter.scripts.data.SceneBossChest();
            meta.boss_chest.monster_config_id = MONSTER_CONFIG_ID;
            meta.boss_chest.resin = 60;
            meta.boss_chest.life_time = 600;
            meta.boss_chest.take_num = 1;
            flower.setMetaGadget(meta);
            flower.buildContent();
            scene.addEntity(flower);
            Grasscutter.getLogger().info("AndriusTrialStartHelper spawned synthetic trounce flower");
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .warn("AndriusTrialStartHelper flower fallback failed: {}", t.toString());
        }
    }

    private static boolean spawnAndriusFromGroupMeta(Scene scene) {
        try {
            SceneScriptManager sm = scene.getScriptManager();
            if (sm == null) {
                return false;
            }
            SceneGroup group = sm.getGroupById(GROUP_ID);
            if (group == null) {
                return false;
            }
            // Prefer official CreateMonster path (title_id / special_name_id from lua).
            sm.spawnMonstersByConfigId(group, MONSTER_CONFIG_ID, 0);
            boolean alive = hasAliveAndrius(scene);
            if (alive) {
                Grasscutter.getLogger()
                        .info("AndriusTrialStartHelper spawned via group meta config {}", MONSTER_CONFIG_ID);
            }
            return alive;
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .debug("AndriusTrialStartHelper spawnAndriusFromGroupMeta: {}", t.toString());
            return false;
        }
    }

    private static boolean spawnAndriusNow(Scene scene, Player player) {
        try {
            MonsterData data = GameData.getMonsterDataMap().get(MONSTER_ID);
            if (data == null) {
                return false;
            }
            int level = Math.max(1, WorldBossSpawnHelper.resolveWorldBossLevel(scene));
            Position pos = new Position(SPAWN_X, SPAWN_Y, SPAWN_Z);
            Position rot = new Position(0f, 155.057f, 0f);
            EntityMonster monster = new EntityMonster(scene, data, pos, rot, level);
            monster.setGroupId(GROUP_ID);
            monster.setConfigId(MONSTER_CONFIG_ID);
            monster.setPoseId(0);
            try {
                SceneMonster meta = new SceneMonster();
                meta.config_id = MONSTER_CONFIG_ID;
                meta.monster_id = MONSTER_ID;
                meta.level = level;
                meta.pose_id = 0;
                meta.drop_id = 1000100;
                meta.title_id = 112;
                meta.special_name_id = 2011102;
                meta.pos = pos.clone();
                meta.rot = rot.clone();
                monster.setMetaMonster(meta);
            } catch (Throwable ignored) {
            }
            scene.addEntity(monster);
            forceClearLimbo(monster);
            Grasscutter.getLogger()
                    .info(
                            "AndriusTrialStartHelper spawned {} for uid={}",
                            MONSTER_ID,
                            player != null ? player.getUid() : 0);
            return true;
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("AndriusTrialStartHelper spawn failed: {}", t.toString());
            return false;
        }
    }
}
