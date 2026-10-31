package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;

/**
 * Client Q cinematic zeros {@code Cur_HPDebts} via {@code ABILITY_META_GLOBAL_FLOAT} /
 * clear-global ~2s before {@code EvtDoSkillSucc}. Block that wipe and pre-arm the consume lock.
 */
public final class ArlecchinoBoLClientFloatGate {
    private static final int ARLECCHINO_AVATAR_ID = 10000096;

    private ArlecchinoBoLClientFloatGate() {}

    public static boolean isBoLKey(String key) {
        return "Cur_HPDebts".equals(key)
                || "_HPDebts".equals(key)
                || "_ABILITY_Cur_HPDebts".equals(key);
    }

    /** @return true if caller must return without applying the client float */
    public static boolean blockClientWipe(GameEntity entity, String key, float value) {
        if (!isBoLKey(key) || !(entity instanceof EntityAvatar av)) {
            return false;
        }
        if (av.getAvatar() == null || av.getAvatar().getAvatarId() != ARLECCHINO_AVATAR_ID) {
            return false;
        }
        if (ArlecchinoBurstBoL.allowAuthoritativeClear(av.getId())) {
            return false;
        }
        float cur = av.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (cur <= 0.5f) {
            return false;
        }
        if (value > Math.max(0.5f, cur * 0.15f)) {
            return false;
        }
        if (!ArlecchinoBurstBoL.isConsumeBlocked(av)) {
            ArlecchinoBurstBoL.onBurstCast(av);
            Grasscutter.getLogger()
                    .info(
                            "[BoL] pre-arm from client GlobalFloat {}={} (server debt={})",
                            key,
                            value,
                            cur);
        } else {
            ArlecchinoBurstBoL.repinClientBoL(av);
            Grasscutter.getLogger()
                    .info("[BoL] skip client GlobalFloat {}={} (consume-lock debt={})", key, value, cur);
        }
        return true;
    }

    /** @return true if caller must return without clearing the global */
    public static boolean blockClientClear(GameEntity entity, String key) {
        if (!isBoLKey(key) || !(entity instanceof EntityAvatar av)) {
            return false;
        }
        if (av.getAvatar() == null || av.getAvatar().getAvatarId() != ARLECCHINO_AVATAR_ID) {
            return false;
        }
        if (ArlecchinoBurstBoL.allowAuthoritativeClear(av.getId())) {
            return false;
        }
        float cur = av.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (cur <= 0.5f) {
            return false;
        }
        if (!ArlecchinoBurstBoL.isConsumeBlocked(av)) {
            ArlecchinoBurstBoL.onBurstCast(av);
        }
        ArlecchinoBurstBoL.repinClientBoL(av);
        Grasscutter.getLogger()
                .info("[BoL] skip client ClearGlobalFloat {} (pre-arm/repin debt={})", key, cur);
        return true;
    }
}
