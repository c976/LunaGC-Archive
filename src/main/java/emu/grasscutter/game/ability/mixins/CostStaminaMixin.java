package emu.grasscutter.game.ability.mixins;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityMixinData;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.managers.stamina.Consumption;
import emu.grasscutter.game.managers.stamina.ConsumptionType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.Grasscutter;

@AbilityMixin(value = AbilityMixinData.Type.CostStaminaMixin)
public class CostStaminaMixin extends AbilityMixinHandler {
    private static final long MAX_COST_INTERVAL = 1000;

    @Override
    public boolean execute(Ability ability, AbilityMixinData mixinData, ByteString abilityData, GameEntity target) {
        float staminaRatio = mixinData.costStaminaDelta.get(ability);
        if (staminaRatio == 0.0f) {
            staminaRatio = 1.0f;
        }

        Player player = ability.getPlayerOwner();
        if (player == null) {
            return false;
        }

        // Nightsoul exploration already pays via NyxCost / phlogiston; skip parallel stamina.
        if (emu.grasscutter.game.ability.NightsoulStaminaExempt.shouldSkipCharacterStamina(player)) {
            return true;
        }

        StaminaManager staminaManager = player.getStaminaManager();
        GameSession session = player.getSession();
        long now = System.currentTimeMillis();
        // Charge for the time since the last tick, a pause longer than the cap bills nothing.
        long gap = now - staminaManager.getLastCostStaminaTime();
        long pastTime = gap > MAX_COST_INTERVAL ? 0 : Math.max(0, gap);
        staminaManager.setLastCostStaminaTime(now);

        // costStaminaDelta is stamina per second in units of 100; a negative delta refunds.
        int costStamina = (int) (staminaRatio * 100 * pastTime / 1000);
        if (costStamina != 0) {
            staminaManager.updateStaminaRelative(
                    session, new Consumption(ConsumptionType.FIGHT, -costStamina), true);
            if (costStamina > 0) staminaManager.staminaRecoverDelay = 0;
        }

        // Not getAbilityName(null): that dereferences its argument, so this threw on every tick of
        // every stamina-costing ability - Mona's sprint above all - and took the mixin with it.
        Grasscutter.getLogger().debug("MIXIN STAMINA CONSUMPTION: Skill={}, Cost={}, PastTime={}",
            ability.getData() != null ? ability.getData().abilityName : null, costStamina, pastTime);

        return true;
    }
}
