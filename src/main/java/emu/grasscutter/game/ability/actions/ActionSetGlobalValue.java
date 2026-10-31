package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.AbilityManager;
import emu.grasscutter.game.ability.ArlecchinoBoLSync;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.MavuikaSpiritHelper;
import emu.grasscutter.game.ability.XilonenC6HealHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/**
 * Writes an ability's global value.
 *
 * <p>V2 is the same write with more fields hung off it - a min/max range, an off-stage flag - none
 * of which this side acts on, so it lands here rather than in a near-copy. Its clamp is deliberately
 * not honoured: V1 carries {@code useLimitRange} too, in 974 places against V2's 235, and applying
 * it here would quietly change the long-standing path as well as the new one.
 */
@AbilityAction({
    AbilityModifierAction.Type.SetGlobalValue,
    AbilityModifierAction.Type.SetGlobalValueV2
})
public final class ActionSetGlobalValue extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
                var properties = propertiesFor(ability);

                var valueKey = action.key;
                float computedValue = action.writtenValue().get(properties, 0f);
                Player owner = ability != null ? ability.getPlayerOwner() : null;
                if (owner == null && target != null && target.getScene() != null) {
                    owner = target.getScene().getHost();
                }
                // Mavuika: Nightsoul spend converting to Fighting Spirit, and the C2 blessing state, both
                // need the old value read before the put.
                if ("NyxValue".equals(valueKey)
                        || "_ABILITY_Mavuika_IsNyxState".equals(valueKey)
                        || "_ABILITY_Mavuika_IsSpecialMove".equals(valueKey)) {
                    try {
                        MavuikaSpiritHelper.beforeGlobalFloatPut(owner, target, valueKey, computedValue);
                    } catch (Throwable ignored) {
                    }
                }
                if ("NyxValue".equals(valueKey) && target != null) {
                    Float oldNyx = target.getGlobalAbilityValues().get(valueKey);
                    if (oldNyx != null && computedValue < oldNyx) {
                        emu.grasscutter.game.ability.NightsoulStaminaExempt.markNyxCostActive(target);
                    }
                }
                // Preserve real Bond on resets; only her own burst can start a settlement.
                if (("Cur_HPDebts".equals(valueKey)
                                || "_HPDebts".equals(valueKey)
                                || "_ABILITY_Cur_HPDebts".equals(valueKey))
                        && computedValue <= 0.5f
                        && target instanceof EntityAvatar av
                        && av.getAvatar() != null
                        && av.getAvatar().getAvatarId() == 10000096) {
                    synchronized (av) {
                        ArlecchinoBurstBoL.tryPreArmFromAbility(ability, av);
                        float debt = av.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
                        if (debt > 0.5f || ArlecchinoBurstBoL.isConsumeBlocked(av)) {
                            av.getGlobalAbilityValues().put(valueKey, debt);
                            ArlecchinoBoLSync.repinUiBar(av);
                            return true;
                        }
                    }
                }
                target.getGlobalAbilityValues().put(valueKey, computedValue);
                target.onAbilityValueUpdate();
                if ("_ABILITY_Xilonen_Constellation_6_IsShowTime".equals(valueKey)) {
                    XilonenC6HealHelper.onGlobalFloat(ability.getPlayerOwner(), target, valueKey, computedValue);
                }
                if ("_ABILITY_Avatar_ForbidFoodHeal".equals(valueKey)
                        && target instanceof EntityAvatar avatar
                        && avatar.getAvatar().getAvatarId() == 10000096) {
                    target.setConvertToHpDebt(computedValue > 0f);
                }

            // Team abilities run against a pseudo-entity that is in no scene, so this fired four
            // NPEs on every single login before the guard.
            if (!AbilityManager.isServerOwnedChain()) {
                var scene = target.getScene();
                var host = scene == null ? null : scene.getHost();
                if (host != null) {
                    host.sendPacket(
                            new PacketServerGlobalValueChangeNotify(target, valueKey, computedValue));
                }
            }
        return true;
    }
}
