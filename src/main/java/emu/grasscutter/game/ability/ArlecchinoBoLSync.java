package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import java.util.concurrent.ConcurrentHashMap;

/** Shared BoL client sync + short post-burst-clear gate against late AddHPDebts. */
public final class ArlecchinoBoLSync {
    private static final long POST_CLEAR_BLOCK_MS = 1500L;

    /** entityId → block AddHPDebts until this time (after Q clear). */
    private static final ConcurrentHashMap<Integer, Long> BLOCK_ADD_UNTIL = new ConcurrentHashMap<>();

    private ArlecchinoBoLSync() {}

    public static void markPostBurstClear(int entityId) {
        BLOCK_ADD_UNTIL.put(entityId, System.currentTimeMillis() + POST_CLEAR_BLOCK_MS);
    }

    public static boolean shouldBlockAddHpDebts(EntityAvatar avatar) {
        if (avatar == null) {
            return false;
        }
        Long until = BLOCK_ADD_UNTIL.get(avatar.getId());
        return until != null && System.currentTimeMillis() < until;
    }

    /**
     * Push FightProp + ability globals the client BoL bar/mesh reads.
     *
     * <p><b>Highest priority:</b> while {@link ArlecchinoBurstBoL#isConsumeBlocked}, any decrease is
     * refused and the client bar is re-pinned. Increases still apply. Authoritative Q clear uses a
     * short bypass inside {@code ArlecchinoBurstBoL.applyClear}.
     */
    public static void pushBoL(EntityAvatar avatar, float newDebt, float change, _ChangeHpDebtsReason reason) {
        if (avatar == null || avatar.getScene() == null) {
            return;
        }
        if (ArlecchinoBurstBoL.isConsumeBlocked(avatar)
                && !ArlecchinoBurstBoL.allowAuthoritativeClear(avatar.getId())) {
            float cur = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
            boolean decreasing = change < -0.01f || newDebt < cur - 0.01f;
            if (decreasing) {
                // Do not lower BoL — re-pin whatever we still hold (or keep cur).
                float keep = Math.max(cur, newDebt);
                avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, keep);
                avatar.getGlobalAbilityValues().put("Cur_HPDebts", keep);
                avatar.getGlobalAbilityValues().put("_HPDebts", keep);
                repinUiBar(avatar);
                return;
            }
        }
        avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, newDebt);
        avatar.getGlobalAbilityValues().put("Cur_HPDebts", newDebt);
        avatar.getGlobalAbilityValues().put("_HPDebts", newDebt);

        var scene = avatar.getScene();
        scene.broadcastPacket(
                new PacketEntityFightPropUpdateNotify(avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        if (change != 0f) {
            scene.broadcastPacket(
                    new PacketEntityFightPropChangeReasonNotify(
                            avatar,
                            FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                            change,
                            PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                            reason));
        }
        scene.broadcastPacket(new PacketServerGlobalValueChangeNotify(avatar, "Cur_HPDebts", newDebt));
        scene.broadcastPacket(new PacketServerGlobalValueChangeNotify(avatar, "_HPDebts", newDebt));
        repinUiBar(avatar);
        // Arm the ~1s re-push so a later client-side predicted wipe self-corrects.
        ArlecchinoBoLUtil.ensureBoLKeepalive(avatar);
    }

    /**
     * {@code AvatarFightPropNotify} is what the UI bar often follows; fight-prop entity packets alone
     * can leave the client showing 0 while server still holds BoL during the delayed Q clear.
     */
    public static void repinUiBar(EntityAvatar avatar) {
        if (avatar == null || avatar.getAvatar() == null || avatar.getPlayer() == null) {
            return;
        }
        if (avatar.getAvatar().getAvatarId() != 10000096) {
            return;
        }
        float debt = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        avatar.getGlobalAbilityValues().put("Cur_HPDebts", debt);
        avatar.getGlobalAbilityValues().put("_HPDebts", debt);
        avatar.getPlayer()
                .sendPacket(
                        new PacketAvatarFightPropUpdateNotify(
                                avatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        if (avatar.getScene() != null) {
            avatar.getScene()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify(
                                    avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            avatar.getScene()
                    .broadcastPacket(
                            new PacketServerGlobalValueChangeNotify(avatar, "Cur_HPDebts", debt));
            avatar.getScene()
                    .broadcastPacket(new PacketServerGlobalValueChangeNotify(avatar, "_HPDebts", debt));
        }
    }
}
