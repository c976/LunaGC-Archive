package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mavuika's Fighting Spirit (special energy) and constellation helper.
 *
 * <p>Fighting Spirit: Nightsoul spent by nearby characters converts 1:1 (+25% at C1), and nearby normal
 * attack hits add 1.5 at most every 0.1s.
 *
 * <p>C2: +200 base ATK while Nightsoul Blessing is active, and -20% DEF on nearby enemies in the Ring of
 * Searing Radiance stance.
 */
public final class MavuikaSpiritHelper {
    public static final int MAVUIKA_ID = 10000106;
    private static final int TALENT_C1 = 1061;
    private static final int TALENT_C2 = 1062;

    private static final String NYX_KEY = "NyxValue";
    private static final String BURST_KEY = "_ABILITY_Mavuika_BurstEnergy";
    private static final String IS_NYX_STATE = "_ABILITY_Mavuika_IsNyxState";
    private static final String IS_SPECIAL_MOVE = "_ABILITY_Mavuika_IsSpecialMove";
    private static final String EXTRA_CONVERT = "_ABILITY_Mavuika_ExtraNyxConvertRatio";

    private static final float NA_GAIN = 1.5f;
    private static final long NA_CD_MS = 100L;
    private static final float DEFAULT_C2_BASE_ATK = 200f;
    private static final float DEFAULT_C2_DEF_SHRED = 0.2f;
    private static final float DEFAULT_C1_CONVERT = 0.25f;
    private static final float DEFAULT_C1_ATK_RATIO = 0.4f;
    private static final int DEFAULT_C1_TIMER_SEC = 8;
    private static final float DEF_AURA_RADIUS = 10f;
    private static final int DEF_REFRESH_SEC = 1;

    private static final Map<Integer, Long> lastNaGainMs = new ConcurrentHashMap<>();
    private static final Map<Long, Float> c2BaseAtkApplied = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> c2BonusLive = new ConcurrentHashMap<>();
    private static final Map<Long, Float> c2LastSeenBase = new ConcurrentHashMap<>();
    private static final Map<Long, Float> c1AtkApplied = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> c1BonusLive = new ConcurrentHashMap<>();
    private static final Map<Long, Float> c1LastSeenAtkPct = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> c1ExpireTask = new ConcurrentHashMap<>();
    private static final Map<Long, Integer> c2KeepaliveTask = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> defShredApplied = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> defAuraTask = new ConcurrentHashMap<>();

    private MavuikaSpiritHelper() {}

    public static void onAttackHit(GameEntity gameEntity) {
        GameEntity attacker = resolveAttacker(gameEntity);
        XilonenC6HealHelper.onAttackHit(attacker);
        if (!(attacker instanceof EntityAvatar entityAvatar)) {
            return;
        }
        Player player = entityAvatar.getPlayer();
        if (player == null) {
            return;
        }
        EntityAvatar mavuika = findMavuikaEntity(player);
        if (mavuika == null) {
            return;
        }
        int uid = player.getUid();
        long now = System.currentTimeMillis();
        Long last = lastNaGainMs.get(uid);
        if (last != null && now - last < NA_CD_MS) {
            return;
        }
        lastNaGainMs.put(uid, now);
        gainSpirit(mavuika, NA_GAIN);
    }

    public static void onNyxValueChanged(Player player, GameEntity gameEntity, float oldValue, float newValue) {
        if (player == null || gameEntity == null) {
            return;
        }
        float consumed = oldValue - newValue;
        if (consumed <= 0.01f) {
            return;
        }
        EntityAvatar mavuika = findMavuikaEntity(player);
        if (mavuika == null) {
            return;
        }
        float ratio = resolveConvertRatio(mavuika);
        gainSpirit(mavuika, consumed * ratio);
    }

    public static void onGlobalFloatPut(Player player, GameEntity gameEntity, String key, float newValue) {
        if (player == null || gameEntity == null || key == null) {
            return;
        }
        if (NYX_KEY.equals(key)) {
            float oldValue = readGlobal(gameEntity, NYX_KEY);
            onNyxValueChanged(player, gameEntity, oldValue, newValue);
            return;
        }
        if (IS_NYX_STATE.equals(key) || IS_SPECIAL_MOVE.equals(key)) {
            refreshC2State(player, gameEntity, key, newValue);
        }
    }

    public static void beforeGlobalFloatPut(Player player, GameEntity gameEntity, String key, float newValue) {
        onGlobalFloatPut(player, gameEntity, key, newValue);
        XilonenC6HealHelper.onGlobalFloat(player, gameEntity, key, newValue);
    }

    /**
     * Optional Avatar.recalcStats hook. After recalc, fight props are rebuilt without our
     * bonuses - mark them dead and reinject once.
     */
    public static void onAfterRecalc(Avatar avatar) {
        if (avatar == null || avatar.getAvatarId() != MAVUIKA_ID) {
            return;
        }
        long guid = avatar.getGuid();
        if (c2BaseAtkApplied.containsKey(guid)) {
            c2BonusLive.put(guid, Boolean.FALSE);
        }
        if (c1AtkApplied.containsKey(guid)) {
            c1BonusLive.put(guid, Boolean.FALSE);
        }
        ensureBonusesLive(avatar);
    }

    private static void ensureBonusesLive(Avatar avatar) {
        if (avatar == null) {
            return;
        }
        long guid = avatar.getGuid();
        boolean changed = false;

        Float c2 = c2BaseAtkApplied.get(guid);
        if (c2 != null && c2 > 0.01f) {
            float cur = avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK);
            Float last = c2LastSeenBase.get(guid);
            boolean live = Boolean.TRUE.equals(c2BonusLive.get(guid));
            if (live && last != null && cur + 0.5f < last) {
                live = false;
            }
            if (!live) {
                avatar.addFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, c2.floatValue());
                c2BonusLive.put(guid, Boolean.TRUE);
                changed = true;
            }
            c2LastSeenBase.put(guid, Float.valueOf(avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK)));
        }

        Float c1 = c1AtkApplied.get(guid);
        if (c1 != null && c1 > 0.01f) {
            float cur = avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT);
            Float last = c1LastSeenAtkPct.get(guid);
            boolean live = Boolean.TRUE.equals(c1BonusLive.get(guid));
            if (live && last != null && cur + 0.01f < last) {
                live = false;
            }
            if (!live) {
                avatar.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, c1.floatValue());
                c1BonusLive.put(guid, Boolean.TRUE);
                changed = true;
            }
            c1LastSeenAtkPct.put(
                    guid, Float.valueOf(avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT)));
        }

        if (changed) {
            recomputeCurAttack(avatar);
            EntityAvatar entity = findEntity(avatar.getPlayer(), guid);
            if (entity != null) {
                syncAttackProps(entity);
            }
        }
    }

    public static void clearPlayerState(int uid) {
        lastNaGainMs.remove(uid);
        Integer task = defAuraTask.remove(uid);
        if (task != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(task);
            } catch (Throwable ignored) {
            }
        }
        // Def shred entries are per-monster; leave them until aura refresh clears.
    }

    private static void gainSpirit(EntityAvatar mavuika, float amount) {
        if (mavuika == null || amount <= 0.01f) {
            return;
        }
        ensureMax(mavuika);
        mavuika.addSpecialEnergy(amount);
        syncBurstEnergy(mavuika);
        applyC1AtkBuff(mavuika);
    }

    private static void applyC1AtkBuff(EntityAvatar mavuika) {
        Avatar avatar = mavuika.getAvatar();
        if (avatar == null || avatar.getCoreProudSkillLevel() < 1) {
            return;
        }
        float[] params = readTalentParams(TALENT_C1);
        float atkRatio = params != null && params.length > 2 ? params[2] : DEFAULT_C1_ATK_RATIO;
        int timerSec = params != null && params.length > 3 ? Math.max(1, Math.round(params[3])) : DEFAULT_C1_TIMER_SEC;
        if (atkRatio <= 0.01f) {
            return;
        }
        long guid = avatar.getGuid();
        if (!Boolean.TRUE.equals(c1BonusLive.get(guid))) {
            avatar.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, atkRatio);
            c1AtkApplied.put(guid, Float.valueOf(atkRatio));
            c1BonusLive.put(guid, Boolean.TRUE);
            c1LastSeenAtkPct.put(
                    guid, Float.valueOf(avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT)));
            recomputeCurAttack(avatar);
            syncAttackProps(mavuika);
        } else {
            ensureBonusesLive(avatar);
        }
        Integer oldTask = c1ExpireTask.remove(guid);
        if (oldTask != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(oldTask);
            } catch (Throwable ignored) {
            }
        }
        int taskId =
                Grasscutter.getGameServer()
                        .getScheduler()
                        .scheduleDelayedTask(
                                () -> {
                                    c1ExpireTask.remove(guid);
                                    try {
                                        Float applied = c1AtkApplied.remove(guid);
                                        boolean live = Boolean.TRUE.equals(c1BonusLive.remove(guid));
                                        c1LastSeenAtkPct.remove(guid);
                                        if (applied == null || applied < 0.01f || !live) {
                                            return;
                                        }
                                        Avatar av = mavuika.getAvatar();
                                        if (av == null) {
                                            return;
                                        }
                                        av.addFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT, -applied.floatValue());
                                        recomputeCurAttack(av);
                                        syncAttackProps(mavuika);
                                    } catch (Throwable ignored) {
                                    }
                                },
                                timerSec);
        c1ExpireTask.put(guid, Integer.valueOf(taskId));
    }

    private static void refreshC2State(Player player, GameEntity source, String key, float newValue) {
        EntityAvatar mavuika = findMavuikaEntity(player);
        if (mavuika == null) {
            return;
        }
        Avatar avatar = mavuika.getAvatar();
        if (avatar == null || avatar.getCoreProudSkillLevel() < 2) {
            clearC2BaseAtk(mavuika);
            stopDefAura(player.getUid());
            clearAllDefShred(player);
            return;
        }

        // Mirror relevant GV onto Mavuika when the write lands on her (or keep local copy).
        boolean sourceIsMavuika =
                source instanceof EntityAvatar
                        && ((EntityAvatar) source).getAvatar() != null
                        && ((EntityAvatar) source).getAvatar().getAvatarId() == MAVUIKA_ID;
        if (sourceIsMavuika && mavuika.getGlobalAbilityValues() != null && key != null) {
            mavuika.getGlobalAbilityValues().put(key, Float.valueOf(newValue));
        }

        boolean inNyx;
        if (IS_NYX_STATE.equals(key) && sourceIsMavuika) {
            inNyx = newValue >= 0.5f;
        } else {
            inNyx = readGlobal(mavuika, IS_NYX_STATE) >= 0.5f;
        }

        boolean ringForm;
        if (IS_SPECIAL_MOVE.equals(key) && sourceIsMavuika) {
            ringForm = newValue < 0.5f;
        } else {
            ringForm = readGlobal(mavuika, IS_SPECIAL_MOVE) < 0.5f;
        }

        float[] params = readTalentParams(TALENT_C2);
        float baseAtk = params != null && params.length > 0 ? params[0] : DEFAULT_C2_BASE_ATK;
        float defShred = params != null && params.length > 1 ? params[1] : DEFAULT_C2_DEF_SHRED;

        if (inNyx) {
            applyC2BaseAtk(mavuika, baseAtk);
            if (ringForm) {
                startDefAura(player, mavuika, defShred);
            } else {
                stopDefAura(player.getUid());
                clearAllDefShred(player);
            }
        } else {
            clearC2BaseAtk(mavuika);
            stopDefAura(player.getUid());
            clearAllDefShred(player);
        }
    }

    private static void applyC2BaseAtk(EntityAvatar mavuika, float amount) {
        Avatar avatar = mavuika.getAvatar();
        if (avatar == null || amount <= 0.01f) {
            return;
        }
        long guid = avatar.getGuid();
        Float prev = c2BaseAtkApplied.get(guid);
        if (prev != null && Math.abs(prev.floatValue() - amount) < 0.01f) {
            ensureBonusesLive(avatar);
            startC2Keepalive(mavuika);
            return;
        }
        if (prev != null && prev > 0.01f && Boolean.TRUE.equals(c2BonusLive.get(guid))) {
            avatar.addFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, -prev.floatValue());
            c2BonusLive.put(guid, Boolean.FALSE);
        }
        avatar.addFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, amount);
        c2BaseAtkApplied.put(guid, Float.valueOf(amount));
        c2BonusLive.put(guid, Boolean.TRUE);
        c2LastSeenBase.put(guid, Float.valueOf(avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK)));
        recomputeCurAttack(avatar);
        syncAttackProps(mavuika);
        startC2Keepalive(mavuika);
    }

    private static void clearC2BaseAtk(EntityAvatar mavuika) {
        Avatar avatar = mavuika != null ? mavuika.getAvatar() : null;
        if (avatar == null) {
            return;
        }
        long guid = avatar.getGuid();
        stopC2Keepalive(guid);
        Float prev = c2BaseAtkApplied.remove(guid);
        c2LastSeenBase.remove(guid);
        boolean live = Boolean.TRUE.equals(c2BonusLive.remove(guid));
        if (prev == null || prev < 0.01f || !live) {
            return;
        }
        avatar.addFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK, -prev.floatValue());
        recomputeCurAttack(avatar);
        syncAttackProps(mavuika);
    }

    private static void startC2Keepalive(EntityAvatar mavuika) {
        Avatar avatar = mavuika.getAvatar();
        if (avatar == null) {
            return;
        }
        long guid = avatar.getGuid();
        if (c2KeepaliveTask.containsKey(guid)) {
            return;
        }
        Player player = mavuika.getPlayer();
        int taskId =
                Grasscutter.getGameServer()
                        .getScheduler()
                        .scheduleDelayedRepeatingTask(
                                () -> {
                                    try {
                                        if (!c2BaseAtkApplied.containsKey(guid)) {
                                            stopC2Keepalive(guid);
                                            return;
                                        }
                                        EntityAvatar current =
                                                player != null ? findMavuikaEntity(player) : mavuika;
                                        if (current == null || current.getAvatar() == null) {
                                            return;
                                        }
                                        if (readGlobal(current, IS_NYX_STATE) < 0.5f) {
                                            clearC2BaseAtk(current);
                                            return;
                                        }
                                        ensureBonusesLive(current.getAvatar());
                                    } catch (Throwable t) {
                                        Grasscutter.getLogger().debug("[MavuikaC2] keepalive: {}", t.toString());
                                    }
                                },
                                2,
                                2);
        c2KeepaliveTask.put(guid, Integer.valueOf(taskId));
    }

    private static void stopC2Keepalive(long guid) {
        Integer task = c2KeepaliveTask.remove(guid);
        if (task != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(task);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void startDefAura(Player player, EntityAvatar mavuika, float shred) {
        int uid = player.getUid();
        if (defAuraTask.containsKey(uid)) {
            applyDefAuraOnce(player, mavuika, shred);
            return;
        }
        applyDefAuraOnce(player, mavuika, shred);
        int taskId =
                Grasscutter.getGameServer()
                        .getScheduler()
                        .scheduleDelayedRepeatingTask(
                                () -> {
                                    try {
                                        EntityAvatar current = findMavuikaEntity(player);
                                        if (current == null || readGlobal(current, IS_NYX_STATE) < 0.5f
                                                || readGlobal(current, IS_SPECIAL_MOVE) >= 0.5f) {
                                            stopDefAura(uid);
                                            clearAllDefShred(player);
                                            return;
                                        }
                                        applyDefAuraOnce(player, current, shred);
                                    } catch (Throwable t) {
                                        Grasscutter.getLogger().debug("[MavuikaC2] def aura: {}", t.toString());
                                    }
                                },
                                DEF_REFRESH_SEC,
                                DEF_REFRESH_SEC);
        defAuraTask.put(uid, Integer.valueOf(taskId));
    }

    private static void stopDefAura(int uid) {
        Integer task = defAuraTask.remove(uid);
        if (task != null) {
            try {
                Grasscutter.getGameServer().getScheduler().cancelTask(task);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void applyDefAuraOnce(Player player, EntityAvatar mavuika, float shred) {
        if (player.getScene() == null || mavuika == null || shred <= 0.01f) {
            return;
        }
        List<Integer> keep = new ArrayList<>();
        for (GameEntity entity : player.getScene().getEntities().values()) {
            if (!(entity instanceof EntityMonster monster)) {
                continue;
            }
            if (distanceFlat(mavuika, monster) > DEF_AURA_RADIUS) {
                continue;
            }
            keep.add(Integer.valueOf(monster.getId()));
            if (!defShredApplied.containsKey(monster.getId())) {
                monster.addFightProperty(FightProperty.FIGHT_PROP_DEFENSE_PERCENT, -shred);
                defShredApplied.put(monster.getId(), Float.valueOf(shred));
                try {
                    player.getScene()
                            .broadcastPacket(
                                    new PacketEntityFightPropUpdateNotify(
                                            monster, FightProperty.FIGHT_PROP_DEFENSE_PERCENT));
                } catch (Throwable ignored) {
                }
            }
        }
        // Remove shred from monsters that left range.
        List<Integer> remove = new ArrayList<>();
        for (Integer id : defShredApplied.keySet()) {
            if (!keep.contains(id)) {
                remove.add(id);
            }
        }
        for (Integer id : remove) {
            Float applied = defShredApplied.remove(id);
            if (applied == null) {
                continue;
            }
            GameEntity entity = player.getScene().getEntityById(id.intValue());
            if (entity != null) {
                entity.addFightProperty(FightProperty.FIGHT_PROP_DEFENSE_PERCENT, applied.floatValue());
                try {
                    player.getScene()
                            .broadcastPacket(
                                    new PacketEntityFightPropUpdateNotify(
                                            entity, FightProperty.FIGHT_PROP_DEFENSE_PERCENT));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void clearAllDefShred(Player player) {
        if (player != null && player.getScene() != null) {
            for (Map.Entry<Integer, Float> entry : new ArrayList<>(defShredApplied.entrySet())) {
                GameEntity entity = player.getScene().getEntityById(entry.getKey().intValue());
                if (entity != null && entry.getValue() != null) {
                    entity.addFightProperty(
                            FightProperty.FIGHT_PROP_DEFENSE_PERCENT, entry.getValue().floatValue());
                    try {
                        player.getScene()
                                .broadcastPacket(
                                        new PacketEntityFightPropUpdateNotify(
                                                entity, FightProperty.FIGHT_PROP_DEFENSE_PERCENT));
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        defShredApplied.clear();
    }

    private static float resolveConvertRatio(EntityAvatar mavuika) {
        float extra = readGlobal(mavuika, EXTRA_CONVERT);
        if (extra > 0.001f) {
            return 1.0f + extra;
        }
        Avatar avatar = mavuika.getAvatar();
        if (avatar != null && avatar.getCoreProudSkillLevel() >= 1) {
            float[] params = readTalentParams(TALENT_C1);
            float fromTalent = params != null && params.length > 1 ? params[1] : DEFAULT_C1_CONVERT;
            return 1.0f + fromTalent;
        }
        return 1.0f;
    }

    private static float[] readTalentParams(int talentId) {
        try {
            AvatarTalentData data = GameData.getAvatarTalentDataMap().get(talentId);
            if (data == null || data.getParamList() == null || data.getParamList().length == 0) {
                return null;
            }
            return data.getParamList();
        } catch (Throwable t) {
            return null;
        }
    }

    private static float readGlobal(GameEntity entity, String key) {
        if (entity == null || key == null) {
            return 0f;
        }
        try {
            Map<String, Float> map = entity.getGlobalAbilityValues();
            if (map == null) {
                return 0f;
            }
            Float v = map.get(key);
            return v != null ? v.floatValue() : 0f;
        } catch (Throwable t) {
            return 0f;
        }
    }

    private static GameEntity resolveAttacker(GameEntity gameEntity) {
        if (gameEntity == null) {
            return null;
        }
        if (gameEntity instanceof EntityClientGadget gadget) {
            try {
                if (gadget.getScene() != null) {
                    GameEntity owner = gadget.getScene().getEntityById(gadget.getOwnerEntityId());
                    if (owner != null) {
                        return owner;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return gameEntity;
    }

    private static EntityAvatar findMavuikaEntity(Player player) {
        if (player == null) {
            return null;
        }
        TeamManager teamManager = player.getTeamManager();
        if (teamManager == null) {
            return null;
        }
        for (EntityAvatar entityAvatar : teamManager.getActiveTeam()) {
            if (entityAvatar == null || entityAvatar.getAvatar() == null) {
                continue;
            }
            if (entityAvatar.getAvatar().getAvatarId() == MAVUIKA_ID) {
                return entityAvatar;
            }
        }
        return null;
    }

    private static EntityAvatar findEntity(Player player, long guid) {
        if (player == null || player.getTeamManager() == null) {
            return null;
        }
        for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
            if (entityAvatar != null
                    && entityAvatar.getAvatar() != null
                    && entityAvatar.getAvatar().getGuid() == guid) {
                return entityAvatar;
            }
        }
        return null;
    }

    private static void ensureMax(EntityAvatar entityAvatar) {
        SpecialEnergyBarHelper.ensureAndSync(entityAvatar);
    }

    private static void syncBurstEnergy(EntityAvatar entityAvatar) {
        float cur = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        Map<String, Float> map = entityAvatar.getGlobalAbilityValues();
        if (map != null) {
            map.put(BURST_KEY, Float.valueOf(cur));
            entityAvatar.onAbilityValueUpdate();
        }
        if (entityAvatar.getPlayer() != null) {
            entityAvatar
                    .getPlayer()
                    .sendPacket(
                            (BasePacket)
                                    new PacketServerGlobalValueChangeNotify(
                                            (GameEntity) entityAvatar, BURST_KEY, cur));
        }
    }

    private static void recomputeCurAttack(Avatar avatar) {
        float base = avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK);
        float percent = avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT);
        float flat = avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK);
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK, base * (1f + percent) + flat);
    }

    private static void syncAttackProps(EntityAvatar entity) {
        if (entity == null || entity.getAvatar() == null) {
            return;
        }
        Avatar avatar = entity.getAvatar();
        entity.setFightProperty(
                FightProperty.FIGHT_PROP_BASE_ATTACK,
                avatar.getFightProperty(FightProperty.FIGHT_PROP_BASE_ATTACK));
        entity.setFightProperty(
                FightProperty.FIGHT_PROP_ATTACK_PERCENT,
                avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK_PERCENT));
        entity.setFightProperty(
                FightProperty.FIGHT_PROP_ATTACK, avatar.getFightProperty(FightProperty.FIGHT_PROP_ATTACK));
        entity.setFightProperty(
                FightProperty.FIGHT_PROP_CUR_ATTACK,
                avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK));
        try {
            if (entity.getScene() != null) {
                entity.getScene()
                        .broadcastPacket(
                                new PacketEntityFightPropUpdateNotify(
                                        entity, FightProperty.FIGHT_PROP_BASE_ATTACK));
                entity.getScene()
                        .broadcastPacket(
                                new PacketEntityFightPropUpdateNotify(
                                        entity, FightProperty.FIGHT_PROP_CUR_ATTACK));
            }
            if (entity.getPlayer() != null) {
                entity.getPlayer().sendPacket(new PacketAvatarFightPropNotify(avatar));
            }
        } catch (Throwable ignored) {
        }
    }

    private static float distanceFlat(GameEntity a, GameEntity b) {
        try {
            var pa = a.getPosition();
            var pb = b.getPosition();
            float dx = pa.getX() - pb.getX();
            float dz = pa.getZ() - pb.getZ();
            return (float) Math.sqrt(dx * dx + dz * dz);
        } catch (Throwable t) {
            return Float.MAX_VALUE;
        }
    }
}
