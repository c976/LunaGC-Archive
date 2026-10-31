package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.ArlecchinoBoLSync;
import emu.grasscutter.game.ability.ClorindeBoLUtil;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.proto.ChangeHpDebtsReason._ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/**
 * ys7.0 AddHPDebts logic adapted to Chiori: JSON {@code value}/{@code ratio} share one field
 * ({@link AbilityModifier.AbilityModifierAction#ratio}). Stack ops like
 * {@code [FIGHT_PROP_MAX_HP, Burst_Cast_BoL_Ratio, MUL]} need fight props in the lookup map.
 */
@AbilityAction(value = AbilityModifier.AbilityModifierAction.Type.AddHPDebts)
public final class ActionAddHPDebts extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability,
            AbilityModifier.AbilityModifierAction action,
            ByteString abilityData,
            GameEntity target) {
        if (ClorindeBoLUtil.handleAddHpDebts(ability, action, target)) {
            return true;
        }

        GameEntity debtTarget = target;
        if (action.target != null && !action.target.isEmpty()) {
            debtTarget = getTarget(ability, ability.getOwner(), action.target);
        }
        if (!(debtTarget instanceof EntityAvatar avatar)) {
            Grasscutter.getLogger().warn("[ActionAddHPDebts] Cannot add HP debt to non-avatar entity");
            return false;
        }

        // After Q clear, late Burst_Cast AddHPDebts used to refill BoL while the client bar
        // stayed empty until a character swap — block those stragglers briefly.
        // Do NOT block ElementalArt (E) reclaim in the same window.
        if (avatar.getAvatar() != null
                && avatar.getAvatar().getAvatarId() == 10000096
                && ArlecchinoBoLSync.shouldBlockAddHpDebts(avatar)
                && isArlecchinoBurstAdd(ability)) {
            Grasscutter.getLogger()
                    .info(
                            "[BoL] skip late Burst AddHPDebts after clear ({})",
                            ability.getData() != null ? ability.getData().abilityName : "?");
            return true;
        }

        float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        if (maxHp <= 0f) {
            return false;
        }

        var properties = new Object2FloatOpenHashMap<String>();
        for (var property : FightProperty.values()) {
            properties.put(property.name(), avatar.getFightProperty(property));
        }
        properties.putAll(ability.getAbilitySpecials());

        // Shared field: "value": [MAX_HP, Ratio, MUL] already yields absolute debt.
        float debt = action.ratio.get(properties, 0f);
        // Talent specials may be 0 on private builds; ExtraAttack reclaim uses these keys.
        if (debt == 0f && avatar.getAvatar() != null && avatar.getAvatar().getAvatarId() == 10000096) {
            if (properties.getOrDefault("HpDebts_Level_1_Ratio", 0f) <= 0f) {
                properties.put("HpDebts_Level_1_Ratio", 0.65f);
            }
            if (properties.getOrDefault("HpDebts_Level_2_Ratio", 0f) <= 0f) {
                properties.put("HpDebts_Level_2_Ratio", 1.30f);
            }
            if (properties.getOrDefault("HpDebts_KillEnemy_Ratio", 0f) <= 0f) {
                properties.put("HpDebts_KillEnemy_Ratio", 1.30f);
            }
            debt = action.ratio.get(properties, 0f);
        }
        if (debt == 0f) {
            return true;
        }

        float currentDebt = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        float newDebt = Math.min(Math.max(currentDebt + debt, 0f), 2f * maxHp);
        float changeDebt = newDebt - currentDebt;

        if (changeDebt == 0f) {
            return true;
        }

        _ChangeHpDebtsReason reason;
        if (newDebt == 0f) {
            reason = _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY_FINISH;
        } else if (changeDebt > 0f) {
            reason = _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_ADD_ABILITY;
        } else {
            reason = _ChangeHpDebtsReason._ChangeHpDebtsReason_CHANGE_HP_DEBTS_PAY;
        }

        if (avatar.getAvatar() != null && avatar.getAvatar().getAvatarId() == 10000096) {
            Grasscutter.getLogger()
                    .info(
                            "[BoL] AddHPDebts +{} → {} via {}",
                            changeDebt,
                            newDebt,
                            ability.getData() != null ? ability.getData().abilityName : "?");
            ArlecchinoBoLSync.pushBoL(avatar, newDebt, changeDebt, reason);
        } else {
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, newDebt);
            avatar.getWorld()
                    .broadcastPacket(
                            new PacketEntityFightPropUpdateNotify(
                                    avatar, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
            avatar.getWorld()
                    .broadcastPacket(
                            new PacketEntityFightPropChangeReasonNotify(
                                    avatar,
                                    FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                                    changeDebt,
                                    PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
                                    reason));
        }

        return true;
    }

    private static boolean isArlecchinoBurstAdd(Ability ability) {
        if (ability == null || ability.getData() == null || ability.getData().abilityName == null) {
            return false;
        }
        String name = ability.getData().abilityName;
        return name.contains("ElementalBurst") || name.contains("Burst_Cast");
    }
}
