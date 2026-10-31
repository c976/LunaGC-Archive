/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.ability.NyxHelper;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class SkirkCunningBridge {
    private static final int SKIRK_E_TAP = 11142;
    private static final int SKIRK_E_HOVER = 11147;
    private static final int SKIRK_Q_BURST = 11145;
    private static final float MIN_E_GAIN = 35.0f;
    private static final float MAX_E_GAIN = 65.0f;
    private static final long AUTH_WINDOW_MS = 1500L;
    private static final long BIG_GAIN_DEDUP_MS = 180L;
    private static final long E_GAIN_MERGE_MS = 900L;
    private static final long TAP_DEDUP_MS = 200L;
    private static final long E_ZERO_GUARD_MS = 250L;
    private static final float DRAIN_PER_TICK = 1.4f;
    private static final long DRAIN_INTERVAL_MS = 200L;
    private static final long TAP_DRAIN_DURATION_MS = 12500L;
    private static final long HOVER_FOLLOWUP_MS = 1500L;
    private static final long DRAIN_ARM_DELAY_MS = 100L;
    private static final long BURST_PAUSE_MS = 5000L;
    private static final long BURST_ECHO_GUARD_MS = 1200L;
    private static final long BURST_COMBO_WINDOW_MS = 3500L;
    private static final String BAR_GV_KEY = "_ABILITY_Avatar_SkirkNew_ElementalArt_Bar";
    private static final String TRANSFORM_FLAG_KEY = "_ABILITY_SkirkNew_ElementalArt_Transform_Flag";
    private static final int MODE_NONE = 0;
    private static final int MODE_HOVER = 1;
    private static final int MODE_SEVEN_FLASH = 2;
    private static final String LEGACY_AUTH_MS_KEY = "_SERVER_SkirkEAuthMs";
    private static final String LEGACY_BIG_GAIN_MS_KEY = "_SERVER_SkirkBigGainMs";
    private static final String LEGACY_E_CAST_MS_KEY = "_SERVER_SkirkECastMs";
    private static final String LEGACY_E_CAST_SKILL_KEY = "_SERVER_SkirkECastSkill";
    private static final int TS_E_CAST = 0;
    private static final int TS_AUTH = 1;
    private static final int TS_BIG_GAIN = 2;
    private static final int TS_LAST_GAIN = 3;
    private static final int TS_LAST_DRAIN = 4;
    private static final int TS_DRAIN_UNTIL = 5;
    private static final int TS_BURST_PAUSE_UNTIL = 6;
    private static final int TS_DRAIN_ARM_UNTIL = 7;
    private static final int TS_BURST_CAST = 8;
    private static final int TS_LAST_TAP_E = 9;
    private static final int TS_DRAIN_FIRST_ACTIVE = 10;
    private static final ConcurrentHashMap<Integer, long[]> TIMESTAMPS = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Integer> E_CAST_SKILLS = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Integer> E_MODES = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Float> TAP_PEAK_CUNNING = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, ScheduledFuture<?>> DRAIN_TASKS = new ConcurrentHashMap();
    private static final ConcurrentHashMap<Integer, Integer> DRAIN_PLAYER_UID = new ConcurrentHashMap();
    /** Serializes bar writes; ability actions run on a thread pool and concurrent +8 absorbs otherwise race. */
    private static final ConcurrentHashMap<Integer, Object> BAR_LOCKS = new ConcurrentHashMap();
    private static final ScheduledExecutorService DRAIN_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkirkSevenFlashDrain");
        thread.setDaemon(true);
        return thread;
    });

    private SkirkCunningBridge() {
    }

    public static void onEnterScene(Player player, EntityAvatar entityAvatar) {
        if (player == null || entityAvatar == null || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        SkirkCunningBridge.clearLegacyTimestampGvs(entityAvatar);
        SkirkCunningBridge.clearSession(entityAvatar.getId());
        SkirkCunningBridge.resetBarGv(player, entityAvatar);
        SkirkCunningBridge.syncBar(player, entityAvatar, 0.0f);
    }

    public static void onAvatarActive(Player player, EntityAvatar entityAvatar) {
        if (player == null || entityAvatar == null || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        SkirkCunningBridge.clearLegacyTimestampGvs(entityAvatar);
        SkirkCunningBridge.clearSession(entityAvatar.getId());
        SkirkCunningBridge.resetBarGv(player, entityAvatar);
        float f = SkirkCunningBridge.safeNyx(entityAvatar);
        float f2 = SkirkCunningBridge.safeSpecial(entityAvatar);
        float f3 = Math.max(f, f2);
        if (f3 > 0.01f) {
            SkirkCunningBridge.syncBar(player, entityAvatar, f3);
            Grasscutter.getLogger().debug("Skirk onAvatarActive keep cunning=" + f3 + " (nyx=" + f + ", special=" + f2 + ")");
        }
    }

    public static void onESkillUiSync(Player player, EntityAvatar entityAvatar, int n) {
        if (player == null || entityAvatar == null || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        if (n != 11142 && n != 11147) {
            return;
        }
        if (n == 11147 && SkirkCunningBridge.shouldIgnore11147(entityAvatar)) {
            Grasscutter.getLogger().debug("Skirk ignore 11147 (seven-flash echo or duplicate hover)");
            return;
        }
        SkirkCunningBridge.refreshHoverSession(entityAvatar);
        if (!SkirkCunningBridge.markESkillCast(entityAvatar, n)) {
            Grasscutter.getLogger().debug("Skirk E skip merge (skill=" + n + ")");
            return;
        }
        long l = System.currentTimeMillis();
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        lArray[1] = l;
        lArray[3] = l;
        SkirkCunningBridge.markBigGain(entityAvatar);
        if (n == 11147) {
            SkirkCunningBridge.enterHoverMode(player, entityAvatar, l);
        } else {
            SkirkCunningBridge.onTapSkillStart(player, entityAvatar, l);
        }
        float f = SkirkCunningBridge.effectiveCunning(entityAvatar);
        float f2 = SkirkCunningBridge.eSkillGain(entityAvatar);
        float f3 = NyxHelper.clampNyx(entityAvatar, f + f2);
        SkirkCunningBridge.syncBar(player, entityAvatar, f3);
        SkirkCunningBridge.alignNyxGv(entityAvatar, f3);
        int n2 = E_MODES.getOrDefault(entityAvatar.getId(), 0);
        if (n == 11142 && !SkirkCunningBridge.wasRecentBurstCast(entityAvatar)) {
            TAP_PEAK_CUNNING.put(entityAvatar.getId(), Float.valueOf(f3));
        }
        Grasscutter.getLogger().debug("Skirk E gain now=" + f3 + " (skill=" + n + ", +=" + f2 + ", was=" + f + ", mode=" + SkirkCunningBridge.modeName(n2) + ", drainOnQ=" + (n == 11142 && n2 != 1) + ")");
    }

    private static void refreshHoverSession(EntityAvatar entityAvatar) {
        if (E_MODES.getOrDefault(entityAvatar.getId(), 0) != 1) {
            return;
        }
        Float f = entityAvatar.getGlobalAbilityValues().get(TRANSFORM_FLAG_KEY);
        if (f == null || f.floatValue() <= 0.01f) {
            E_MODES.put(entityAvatar.getId(), 0);
            Grasscutter.getLogger().debug("Skirk hover session ended (Transform_Flag=0)");
        }
    }

    private static void enterHoverMode(Player player, EntityAvatar entityAvatar, long l) {
        E_MODES.put(entityAvatar.getId(), 1);
        SkirkCunningBridge.deactivateDrain(entityAvatar);
        SkirkCunningBridge.setBarGv(player, entityAvatar, 1.0f);
        Grasscutter.getLogger().debug("Skirk mode=HOVER (11147), drain disabled for hover session");
    }

    private static void onTapSkillStart(Player player, EntityAvatar entityAvatar, long l) {
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        lArray[9] = l;
        if (SkirkCunningBridge.wasRecentBurstCast(entityAvatar)) {
            Grasscutter.getLogger().debug("Skirk E tap (burst combo echo, no drain arm)");
            return;
        }
        if (E_MODES.getOrDefault(entityAvatar.getId(), 0) == 1) {
            Grasscutter.getLogger().debug("Skirk 11142 during hover session, no drain");
            return;
        }
        E_MODES.put(entityAvatar.getId(), 0);
        SkirkCunningBridge.deactivateDrain(entityAvatar);
        Grasscutter.getLogger().debug("Skirk tap E: cunning gained, drain waits for Q combo");
    }

    private static boolean shouldIgnore11147(EntityAvatar entityAvatar) {
        if (SkirkCunningBridge.isBurstPause(entityAvatar)) {
            return true;
        }
        int n = E_MODES.getOrDefault(entityAvatar.getId(), 0);
        if (n == 1) {
            return true;
        }
        if (n == 2) {
            return true;
        }
        if (SkirkCunningBridge.isDrainActive(entityAvatar) || SkirkCunningBridge.isDrainScheduled(entityAvatar)) {
            return true;
        }
        long[] lArray = TIMESTAMPS.get(entityAvatar.getId());
        if (lArray != null && lArray[9] > 0L) {
            return System.currentTimeMillis() - lArray[9] > 1500L;
        }
        return false;
    }

    public static void onBurstSkill(Player player, EntityAvatar entityAvatar) {
        boolean bl;
        if (player == null || entityAvatar == null || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        long l = System.currentTimeMillis();
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        if (lArray[8] > 0L && l - lArray[8] < 1200L) {
            Grasscutter.getLogger().debug("Skirk burst debounce skip");
            return;
        }
        int n = E_MODES.getOrDefault(entityAvatar.getId(), 0);
        boolean bl2 = n == 1;
        boolean bl3 = bl = !bl2 && lArray[9] > 0L && l - lArray[9] < 3500L;
        Float f = TAP_PEAK_CUNNING.get(entityAvatar.getId());
        lArray[8] = l;
        float f2 = Math.max(SkirkCunningBridge.safeSpecial(entityAvatar), SkirkCunningBridge.safeNyx(entityAvatar));
        if (f != null && f.floatValue() > f2) {
            f2 = f.floatValue();
        }
        if (bl3) {
            if (f2 < 50.0f) {
                f2 = Math.max(f2, 50.0f);
            }
            SkirkCunningBridge.setBarGv(player, entityAvatar, 2.0f);
            f2 = NyxHelper.clampNyx(entityAvatar, f2 - 50.0f);
            Grasscutter.getLogger().debug("Skirk E\u2192Q burst cost -50 -> " + f2);
            SkirkCunningBridge.syncBar(player, entityAvatar, f2);
            SkirkCunningBridge.alignNyxGv(entityAvatar, f2);
            E_MODES.put(entityAvatar.getId(), 2);
            SkirkCunningBridge.activateSevenFlashDrain(entityAvatar, l);
            lArray[10] = 0L;
            lArray[4] = l - 200L;
            lArray[6] = 0L;
            SkirkCunningBridge.ensureDrainTicker(player, entityAvatar);
            Grasscutter.getLogger().debug("Skirk E\u2192Q combo: seven-flash drain started on Q");
        } else if (bl2) {
            SkirkCunningBridge.deactivateDrain(entityAvatar);
            SkirkCunningBridge.stopDrainTicker(entityAvatar.getId());
            lArray[6] = l + 5000L;
            if (f2 > 0.01f) {
                SkirkCunningBridge.syncBar(player, entityAvatar, f2);
                SkirkCunningBridge.alignNyxGv(entityAvatar, f2);
            }
            Grasscutter.getLogger().debug("Skirk hover Q (Exhaust): keep cunning=" + f2);
        } else {
            SkirkCunningBridge.deactivateDrain(entityAvatar);
            SkirkCunningBridge.stopDrainTicker(entityAvatar.getId());
            lArray[6] = l + 5000L;
            float f3 = f2;
            f2 = 0.0f;
            SkirkCunningBridge.syncBar(player, entityAvatar, 0.0f);
            SkirkCunningBridge.alignNyxGv(entityAvatar, 0.0f);
            Grasscutter.getLogger().debug("Skirk solo Q (Extinguish): consume all cunning " + f3 + " -> 0");
        }
        if (!bl3) {
            TAP_PEAK_CUNNING.remove(entityAvatar.getId());
        }
        Grasscutter.getLogger().debug("Skirk burst skill=11145 cunning=" + f2 + " combo=" + bl + " hover=" + bl2 + " comboDrain=" + bl3);
    }

    public static void onClientBurstAttempt(Player player, GameEntity gameEntity) {
        EntityAvatar entityAvatar;
        if (player == null || !(gameEntity instanceof EntityAvatar) || !NyxHelper.isSkirkEntity(entityAvatar = (EntityAvatar)gameEntity)) {
            return;
        }
        SkirkCunningBridge.onBurstSkill(player, entityAvatar);
    }

    public static void applyDelta(Player player, EntityAvatar entityAvatar, float f) {
        if (player == null || entityAvatar == null || f == 0.0f || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        // AbilityManager.executeAction submits to a pool; 3 rift absorbs can run concurrently and
        // all read the same bar before any write commits — only one +8 would stick.
        synchronized (SkirkCunningBridge.barLock(entityAvatar.getId())) {
            float f2 = SkirkCunningBridge.effectiveCunning(entityAvatar);
            if (f > 0.0f && SkirkCunningBridge.isBigGain(f)) {
                if (SkirkCunningBridge.isDrainScheduled(entityAvatar) || SkirkCunningBridge.isDrainActive(entityAvatar)) {
                    Grasscutter.getLogger().debug("Skirk applyDelta block +" + f + " during tap drain (cur=" + f2 + ")");
                    return;
                }
                if (!SkirkCunningBridge.isAuthorized(entityAvatar)) {
                    Grasscutter.getLogger().debug("Skirk applyDelta block unauthorized +" + f + " (cur=" + f2 + ")");
                    return;
                }
                if (!SkirkCunningBridge.markBigGain(entityAvatar)) {
                    Grasscutter.getLogger().debug("Skirk applyDelta dedup +" + f);
                    return;
                }
                float f3 = NyxHelper.clampNyx(entityAvatar, f2 + f);
                SkirkCunningBridge.syncBar(player, entityAvatar, f3);
                Grasscutter.getLogger().debug("Skirk invoke gain now=" + f3 + " (+" + f + ", was=" + f2 + ")");
                return;
            }
            if (f > 0.0f && f >= 9.5f && f <= 10.5f && SkirkCunningBridge.isAuthorized(entityAvatar) && SkirkCunningBridge.wasRecentBigGain(entityAvatar)) {
                Grasscutter.getLogger().debug("Skirk applyDelta dedup small +" + f);
                return;
            }
            if (f < 0.0f && (SkirkCunningBridge.isDrainActive(entityAvatar) || SkirkCunningBridge.isDrainScheduled(entityAvatar))) {
                Grasscutter.getLogger().debug("Skirk applyDelta ignore client/ability drain " + f + " (server owns seven-flash ticks, cur=" + f2 + ")");
                return;
            }
            float f3 = NyxHelper.clampNyx(entityAvatar, f2 + f);
            SkirkCunningBridge.syncBar(player, entityAvatar, f3);
            if (f > 0.0f && f < 35.0f) {
                Grasscutter.getLogger().debug("Skirk rift/small gain now=" + f3 + " (+" + f + ", was=" + f2 + ")");
            }
        }
    }

    private static Object barLock(int entityId) {
        return BAR_LOCKS.computeIfAbsent(entityId, id -> new Object());
    }

    public static float eSkillGain(EntityAvatar entityAvatar) {
        float f = 45.0f;
        if (entityAvatar != null && entityAvatar.getAvatar() != null && entityAvatar.getAvatar().getTalentIdList().contains(1142)) {
            f += 10.0f;
        }
        return f;
    }

    public static void onNyxAdd(Player player, EntityAvatar entityAvatar, float f) {
        if (player == null || entityAvatar == null || f == 0.0f || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        float f2 = SkirkCunningBridge.safeNyx(entityAvatar);
        float f3 = SkirkCunningBridge.safeSpecial(entityAvatar);
        if (f < 0.0f && (SkirkCunningBridge.isDrainActive(entityAvatar) || SkirkCunningBridge.isDrainScheduled(entityAvatar))) {
            Grasscutter.getLogger().debug("Skirk NyxAdd ignore drain " + f + " during seven-flash ticks");
            return;
        }
        if (f > 0.0f && SkirkCunningBridge.isBigGain(f)) {
            if (SkirkCunningBridge.isDrainScheduled(entityAvatar) || SkirkCunningBridge.isDrainActive(entityAvatar)) {
                Grasscutter.getLogger().debug("Skirk NyxAdd block +" + f + " during tap drain");
                return;
            }
            if (f3 >= 35.0f && Math.abs(f - f3) <= 1.0f) {
                SkirkCunningBridge.syncBar(player, entityAvatar, f3);
                Grasscutter.getLogger().debug("Skirk NyxAdd align special=" + f3 + " (delta=" + f + ")");
                return;
            }
            if (Math.abs(f2 - f3) <= 1.0f && f3 >= 35.0f) {
                Grasscutter.getLogger().debug("Skirk NyxAdd skip stack (already synced cur=" + f2 + ")");
                return;
            }
        }
        SkirkCunningBridge.syncBar(player, entityAvatar, NyxHelper.clampNyx(entityAvatar, f2 + f));
    }

    public static void tickModeDrain(Player player, EntityAvatar entityAvatar) {
        if (player == null || entityAvatar == null || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        SkirkCunningBridge.refreshHoverSession(entityAvatar);
        if (E_MODES.getOrDefault(entityAvatar.getId(), 0) == 1) {
            SkirkCunningBridge.stopDrainTicker(entityAvatar.getId());
            return;
        }
        if (!SkirkCunningBridge.isDrainActive(entityAvatar)) {
            if (!SkirkCunningBridge.isDrainScheduled(entityAvatar)) {
                SkirkCunningBridge.stopDrainTicker(entityAvatar.getId());
            }
            return;
        }
        float f = SkirkCunningBridge.safeSpecial(entityAvatar);
        if (f <= 0.01f) {
            SkirkCunningBridge.deactivateDrain(entityAvatar);
            E_MODES.put(entityAvatar.getId(), 0);
            SkirkCunningBridge.stopDrainTicker(entityAvatar.getId());
            return;
        }
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        long l = System.currentTimeMillis();
        if (lArray[10] <= 0L) {
            lArray[10] = l;
        }
        if (lArray[4] <= 0L) {
            lArray[4] = lArray[3] > 0L ? lArray[3] : l;
            return;
        }
        long l2 = l - lArray[4];
        if (l2 < 200L) {
            return;
        }
        int n = (int)Math.min(10L, l2 / 200L);
        float f2 = (float)n * 1.4f;
        synchronized (SkirkCunningBridge.barLock(entityAvatar.getId())) {
            float cur = SkirkCunningBridge.safeSpecial(entityAvatar);
            float f3 = NyxHelper.clampNyx(entityAvatar, cur - f2);
            if (f3 >= cur - 0.01f) {
                return;
            }
            lArray[4] = l;
            SkirkCunningBridge.syncBar(player, entityAvatar, f3);
            if (f3 <= 0.01f) {
                SkirkCunningBridge.deactivateDrain(entityAvatar);
                E_MODES.put(entityAvatar.getId(), 0);
                TAP_PEAK_CUNNING.remove(entityAvatar.getId());
                SkirkCunningBridge.stopDrainTicker(entityAvatar.getId());
            }
            Grasscutter.getLogger().debug("Skirk seven-flash drain tick -> " + f3 + " (-" + f2 + ", was=" + cur + ")");
        }
    }

    private static void ensureDrainTicker(Player player, EntityAvatar entityAvatar) {
        if (player == null || entityAvatar == null) {
            return;
        }
        int n = entityAvatar.getId();
        DRAIN_PLAYER_UID.put(n, player.getUid());
        ScheduledFuture<?> scheduledFuture = DRAIN_TASKS.get(n);
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            return;
        }
        ScheduledFuture<?> scheduledFuture2 = DRAIN_SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                Integer n2 = DRAIN_PLAYER_UID.get(n);
                if (n2 == null) {
                    SkirkCunningBridge.stopDrainTicker(n);
                    return;
                }
                Player currentPlayer = Grasscutter.getGameServer() != null ? Grasscutter.getGameServer().getPlayerByUid(n2) : null;
                if (currentPlayer == null || currentPlayer.getScene() == null) {
                    SkirkCunningBridge.stopDrainTicker(n);
                    return;
                }
                GameEntity gameEntity = currentPlayer.getScene().getEntityById(n);
                if (!(gameEntity instanceof EntityAvatar) || !NyxHelper.isSkirkEntity((EntityAvatar) gameEntity)) {
                    SkirkCunningBridge.stopDrainTicker(n);
                    return;
                }
                SkirkCunningBridge.tickModeDrain(currentPlayer, (EntityAvatar) gameEntity);
            }
            catch (Throwable throwable) {
                Grasscutter.getLogger().warn("Skirk drain ticker failed: {}", (Object)throwable.toString());
            }
        }, 200L, 200L, TimeUnit.MILLISECONDS);
        DRAIN_TASKS.put(n, scheduledFuture2);
    }

    private static void stopDrainTicker(int n) {
        ScheduledFuture<?> scheduledFuture = DRAIN_TASKS.remove(n);
        DRAIN_PLAYER_UID.remove(n);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    public static void syncFromClient(Player player, EntityAvatar entityAvatar, float f) {
        if (player == null || entityAvatar == null || Float.isNaN(f) || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        boolean bl = SkirkCunningBridge.isBurstPause(entityAvatar);
        if (!bl) {
            SkirkCunningBridge.tickModeDrain(player, entityAvatar);
        }
        float f2 = SkirkCunningBridge.safeNyx(entityAvatar);
        float f3 = SkirkCunningBridge.safeSpecial(entityAvatar);
        float f4 = SkirkCunningBridge.effectiveCunning(entityAvatar);
        if (bl) {
            if (f <= 0.01f) {
                if (f4 >= 50.0f) {
                    float f5 = NyxHelper.clampNyx(entityAvatar, f4 - 50.0f);
                    SkirkCunningBridge.syncBar(player, entityAvatar, f5);
                    SkirkCunningBridge.alignNyxGv(entityAvatar, f5);
                    Grasscutter.getLogger().debug("Skirk burst cost -50 -> " + f5 + " (was=" + f4 + ")");
                } else {
                    SkirkCunningBridge.alignNyxGvOnly(player, entityAvatar, 0.0f);
                    Grasscutter.getLogger().debug("Skirk burst nyx gv align 0 (special=" + f3 + ", effective=" + f4 + ")");
                }
                return;
            }
            if (f < f4 - 0.01f) {
                SkirkCunningBridge.syncBar(player, entityAvatar, f);
                Grasscutter.getLogger().debug("Skirk burst cost sync -> " + f + " (was=" + f4 + ")");
                return;
            }
            if (Math.abs(f - f2) < 0.01f && Math.abs(f - f3) < 0.01f) {
                return;
            }
            SkirkCunningBridge.syncBar(player, entityAvatar, f);
            return;
        }
        if (SkirkCunningBridge.isDrainActive(entityAvatar) || SkirkCunningBridge.isDrainScheduled(entityAvatar)) {
            if (f <= 0.01f || f < f3 - 0.01f) {
                Grasscutter.getLogger().debug("Skirk ignore client drain desync (target=" + f + ", nyx=" + f2 + ", special=" + f3 + ")");
            } else if (f > f3 + 1.0f && f >= 99.0f) {
                Grasscutter.getLogger().debug("Skirk ignore client cap spike during tap mode (target=" + f + ", special=" + f3 + ")");
            }
            return;
        }
        if (f <= 0.01f && f4 >= 35.0f) {
            Grasscutter.getLogger().debug("Skirk ignore client zero desync (nyx=" + f2 + ", special=" + f3 + ")");
            return;
        }
        if (f > 0.01f && f < f4 - 0.01f && SkirkCunningBridge.wasRecentBigGain(entityAvatar)) {
            Grasscutter.getLogger().debug("Skirk ignore client nyx drop after E gain (effective=" + f4 + ", target=" + f + ")");
            return;
        }
        if (Math.abs(f - f2) < 0.01f && Math.abs(f - f3) < 0.01f) {
            return;
        }
        Grasscutter.getLogger().debug("Skirk client sync target=" + f + " (nyx=" + f2 + ", special=" + f3 + ")");
        SkirkCunningBridge.syncBar(player, entityAvatar, f);
    }

    public static void syncBar(Player player, EntityAvatar entityAvatar, float f) {
        if (player == null || entityAvatar == null || !NyxHelper.isSkirkEntity(entityAvatar)) {
            return;
        }
        NyxHelper.ensureSkirkNyxBounds(entityAvatar);
        float f2 = SkirkCunningBridge.safeNyx(entityAvatar);
        float f3 = NyxHelper.clampNyx(entityAvatar, f);
        float f4 = f3 - f2;
        entityAvatar.getGlobalAbilityValues().put("NyxValue", Float.valueOf(f3));
        entityAvatar.getGlobalAbilityValues().put("NyxValueMax", Float.valueOf(100.0f));
        entityAvatar.getGlobalAbilityValues().put("NyxValueMin", Float.valueOf(0.0f));
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 100.0f);
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, 50.0f);
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, f3);
        entityAvatar.getAvatar().setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, 100.0f);
        entityAvatar.getAvatar().setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, 50.0f);
        entityAvatar.getAvatar().setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, f3);
        entityAvatar.onAbilityValueUpdate();
        player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, "NyxValue", f3));
        player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, "NyxValueMax", 100.0f));
        player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, "NyxValueMin", 0.0f));
        PacketEntityFightPropUpdateNotify packetEntityFightPropUpdateNotify = new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, Arrays.asList(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY));
        player.sendPacket(packetEntityFightPropUpdateNotify);
        if (entityAvatar.getScene() != null) {
            entityAvatar.getScene().broadcastPacket(packetEntityFightPropUpdateNotify);
        }
        player.sendPacket(new PacketAvatarFightPropUpdateNotify(entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
        player.sendPacket(new PacketAvatarFightPropUpdateNotify(entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
        player.sendPacket(new PacketAvatarFightPropUpdateNotify(entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY));
        if (Math.abs(f4) > 0.001f) {
            PacketEntityFightPropChangeReasonNotify packetEntityFightPropChangeReasonNotify = new PacketEntityFightPropChangeReasonNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, Float.valueOf(f4), PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY);
            player.sendPacket(packetEntityFightPropChangeReasonNotify);
            if (entityAvatar.getScene() != null) {
                entityAvatar.getScene().broadcastPacket(packetEntityFightPropChangeReasonNotify);
            }
        }
        Grasscutter.getLogger().debug("Skirk cunning sync now=" + f3);
    }

    private static boolean markESkillCast(EntityAvatar entityAvatar, int n) {
        long l = System.currentTimeMillis();
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        if (lArray[3] > 0L && l - lArray[3] < 900L) {
            return false;
        }
        if (lArray[0] > 0L) {
            long l2 = l - lArray[0];
            if (n == 11142 && l2 < 200L) {
                return false;
            }
            Integer n2 = E_CAST_SKILLS.get(entityAvatar.getId());
            if (n == 11147 && n2 != null && n2 == 11147 && l2 < 200L) {
                return false;
            }
        }
        lArray[0] = l;
        E_CAST_SKILLS.put(entityAvatar.getId(), n);
        return true;
    }

    private static boolean isAuthorized(EntityAvatar entityAvatar) {
        long[] lArray = TIMESTAMPS.get(entityAvatar.getId());
        if (lArray == null || lArray[1] <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - lArray[1] < 1500L;
    }

    private static boolean wasRecentBigGain(EntityAvatar entityAvatar) {
        long[] lArray = TIMESTAMPS.get(entityAvatar.getId());
        if (lArray == null || lArray[2] <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - lArray[2] < 250L;
    }

    private static boolean markBigGain(EntityAvatar entityAvatar) {
        long l = System.currentTimeMillis();
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        if (lArray[2] > 0L && l - lArray[2] < 180L) {
            return false;
        }
        lArray[2] = l;
        return true;
    }

    private static boolean isBigGain(float f) {
        return f >= 35.0f && f <= 65.0f;
    }

    private static void activateSevenFlashDrain(EntityAvatar entityAvatar, long l) {
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        lArray[7] = l + 100L;
        lArray[5] = l + 100L + 12500L;
    }

    private static void deactivateDrain(EntityAvatar entityAvatar) {
        long[] lArray = SkirkCunningBridge.timestamps(entityAvatar);
        lArray[5] = 0L;
        lArray[7] = 0L;
        lArray[10] = 0L;
        SkirkCunningBridge.stopDrainTicker(entityAvatar.getId());
    }

    private static boolean isDrainScheduled(EntityAvatar entityAvatar) {
        if (E_MODES.getOrDefault(entityAvatar.getId(), 0) == 1) {
            return false;
        }
        long[] lArray = TIMESTAMPS.get(entityAvatar.getId());
        if (lArray == null || lArray[5] <= 0L) {
            return false;
        }
        return System.currentTimeMillis() < lArray[5];
    }

    private static boolean isDrainActive(EntityAvatar entityAvatar) {
        if (E_MODES.getOrDefault(entityAvatar.getId(), 0) != 2) {
            return false;
        }
        long[] lArray = TIMESTAMPS.get(entityAvatar.getId());
        if (lArray == null || lArray[5] <= 0L) {
            return false;
        }
        long l = System.currentTimeMillis();
        if (l < lArray[7]) {
            return false;
        }
        if (l >= lArray[5]) {
            return false;
        }
        return lArray[6] <= 0L || l >= lArray[6];
    }

    private static boolean wasRecentBurstCast(EntityAvatar entityAvatar) {
        long[] lArray = TIMESTAMPS.get(entityAvatar.getId());
        if (lArray == null || lArray[8] <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - lArray[8] < 1200L;
    }

    private static boolean isBurstPause(EntityAvatar entityAvatar) {
        long[] lArray = TIMESTAMPS.get(entityAvatar.getId());
        if (lArray == null || lArray[6] <= 0L) {
            return false;
        }
        return System.currentTimeMillis() < lArray[6];
    }

    private static String modeName(int n) {
        return switch (n) {
            case 1 -> "HOVER";
            case 2 -> "SEVEN_FLASH";
            default -> "NONE";
        };
    }

    private static void setBarGv(Player player, EntityAvatar entityAvatar, float f) {
        float f2 = f < 1.0f ? 1.0f : (f > 2.0f ? 2.0f : f);
        entityAvatar.getGlobalAbilityValues().put(BAR_GV_KEY, Float.valueOf(f2));
        if (player != null) {
            player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, BAR_GV_KEY, f2));
        }
    }

    private static void alignNyxGvOnly(Player player, EntityAvatar entityAvatar, float f) {
        float f2 = NyxHelper.clampNyx(entityAvatar, f);
        entityAvatar.getGlobalAbilityValues().put("NyxValue", Float.valueOf(f2));
        if (player != null) {
            player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, "NyxValue", f2));
        }
    }

    private static float effectiveCunning(EntityAvatar entityAvatar) {
        return Math.max(SkirkCunningBridge.safeNyx(entityAvatar), SkirkCunningBridge.safeSpecial(entityAvatar));
    }

    private static void alignNyxGv(EntityAvatar entityAvatar, float f) {
        entityAvatar.getGlobalAbilityValues().put("NyxValue", Float.valueOf(f));
    }

    private static void resetBarGv(Player player, EntityAvatar entityAvatar) {
        Float f = entityAvatar.getGlobalAbilityValues().get(BAR_GV_KEY);
        if (f != null && f.floatValue() >= 1.0f && f.floatValue() <= 2.0f) {
            return;
        }
        entityAvatar.getGlobalAbilityValues().put(BAR_GV_KEY, Float.valueOf(1.0f));
        player.sendPacket(new PacketServerGlobalValueChangeNotify(entityAvatar, BAR_GV_KEY, 1.0f));
    }

    private static void clearLegacyTimestampGvs(EntityAvatar entityAvatar) {
        entityAvatar.getGlobalAbilityValues().remove(LEGACY_AUTH_MS_KEY);
        entityAvatar.getGlobalAbilityValues().remove(LEGACY_BIG_GAIN_MS_KEY);
        entityAvatar.getGlobalAbilityValues().remove(LEGACY_E_CAST_MS_KEY);
        entityAvatar.getGlobalAbilityValues().remove(LEGACY_E_CAST_SKILL_KEY);
    }

    private static void clearSession(int n) {
        TIMESTAMPS.remove(n);
        E_CAST_SKILLS.remove(n);
        TAP_PEAK_CUNNING.remove(n);
        E_MODES.remove(n);
        BAR_LOCKS.remove(n);
        SkirkCunningBridge.stopDrainTicker(n);
    }

    public static void clearPlayerState(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        for (EntityAvatar entityAvatar : new ArrayList<>(player.getTeamManager().getActiveTeam())) {
            if (entityAvatar != null) {
                clearSession(entityAvatar.getId());
            }
        }
    }

    public static void clearEntityState(int entityId) {
        clearSession(entityId);
    }

    private static long[] timestamps(EntityAvatar entityAvatar) {
        return TIMESTAMPS.computeIfAbsent(entityAvatar.getId(), n -> new long[11]);
    }

    private static float safeNyx(EntityAvatar entityAvatar) {
        float f = NyxHelper.getNyx(entityAvatar);
        return Float.isNaN(f) ? 0.0f : f;
    }

    private static float safeSpecial(EntityAvatar entityAvatar) {
        float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        return Float.isNaN(f) ? 0.0f : f;
    }
}
