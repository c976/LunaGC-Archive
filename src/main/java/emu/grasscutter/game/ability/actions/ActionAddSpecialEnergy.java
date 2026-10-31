/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.NyxHelper;
import emu.grasscutter.game.ability.SkirkCunningBridge;
import emu.grasscutter.game.ability.SkirkCunningHelper;
import emu.grasscutter.game.ability.SpecialEnergyBarHelper;
import emu.grasscutter.game.ability.actions.AbilityAction;
import emu.grasscutter.game.ability.actions.AbilityActionHandler;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.server.packet.send.PacketServerGlobalValueChangeNotify;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.List;
import java.util.Map;

@AbilityAction(value=AbilityModifier.AbilityModifierAction.Type.AddSpecialEnergy)
public final class ActionAddSpecialEnergy
extends AbilityActionHandler {
    private static final String BURST_ENERGY_KEY = "_ABILITY_Mavuika_BurstEnergy";

    @Override
    public boolean execute(Ability ability, AbilityModifier.AbilityModifierAction abilityModifierAction, ByteString byteString, GameEntity gameEntity) {
        Float f;
        GameEntity gameEntity2;
        GameEntity gameEntity3 = gameEntity2 = gameEntity != null ? gameEntity : ability.getOwner();
        if (gameEntity2 == null) {
            return false;
        }
        SkirkCunningHelper.ensureC2ExtraEnergySpecial(ability, gameEntity2);
        SkirkCunningHelper.ensurePickableEnergyReviveSpecial(ability, gameEntity2);
        Object2FloatOpenHashMap<String> object2FloatOpenHashMap = new Object2FloatOpenHashMap<String>();
        FightProperty[] fightPropertyArray = FightProperty.values();
        int n = fightPropertyArray.length;
        for (int i = 0; i < n; ++i) {
            FightProperty fightProperty = fightPropertyArray[i];
            object2FloatOpenHashMap.put(fightProperty.name(), gameEntity2.getFightProperty(fightProperty));
        }
        object2FloatOpenHashMap.putAll((Map<String, Float>)ability.getAbilitySpecials());
        float f2 = 0.0f;
        if (abilityModifierAction.ratio != null) {
            f2 = abilityModifierAction.ratio.get(object2FloatOpenHashMap, 0.0f);
        }
        if (f2 == 0.0f && abilityModifierAction.amount != null) {
            f2 = abilityModifierAction.amount.get(object2FloatOpenHashMap, 0.0f);
        }
        if (Math.abs(f2) < 0.01f && SkirkCunningHelper.isSkirk(gameEntity2) && ActionAddSpecialEnergy.isExtraEnergyRatio(abilityModifierAction.ratio) && (f = ability.getAbilitySpecials().get("SkirkNew_Constellation_2_ExtraEnergy")) != null && f.floatValue() >= 9.5f) {
            f2 = f.floatValue();
        }
        // Rift energy: the config has ratio=0 with value=SkirkNew_Pickable_Energy_Revive, and Gson keeps
        // only ratio, so the value is resolved explicitly here.
        if (Math.abs(f2) < 0.01f && SkirkCunningHelper.isSkirk(gameEntity2)) {
            float revive = SkirkCunningHelper.resolvePickableEnergyRevive(ability);
            if (revive >= 0.5f) {
                f2 = revive;
            } else if (SkirkCunningHelper.isPickableEnergyReviveRatio(abilityModifierAction.ratio)) {
                Float patched = ability.getAbilitySpecials().get(SkirkCunningHelper.PICKABLE_ENERGY_REVIVE_KEY);
                if (patched != null && patched.floatValue() >= 0.5f) {
                    f2 = patched.floatValue();
                }
            }
        }
        int n2 = n = abilityModifierAction.ratio != null && abilityModifierAction.ratio.isDynamic() || abilityModifierAction.amount != null && abilityModifierAction.amount.isDynamic() || abilityModifierAction.ratio != null && abilityModifierAction.ratio.getConstant() != 0.0f || abilityModifierAction.amount != null && abilityModifierAction.amount.getConstant() != 0.0f ? 1 : 0;
        // The Mavuika-style default of +1.5 must not be applied to Skirk's rift absorb, whose ratio is
        // deliberately 0.
        if (f2 == 0.0f && n == 0 && !SkirkCunningHelper.isPickableEnergyAbility(ability)) {
            f2 = 1.5f;
        }
        if (NyxHelper.isSkirkEntity(gameEntity2) && gameEntity2 instanceof EntityAvatar) {
            Player player = ability.getPlayerOwner();
            if (player == null && gameEntity2.getScene() != null) {
                player = gameEntity2.getScene().getHost();
            }
            // Rift energy can also arrive via a GV; dedupe per absorb so only one +8 is granted.
            if (f2 > 0.5f
                    && f2 < 35.0f
                    && SkirkCunningHelper.isPickableEnergyAbility(ability)
                    && !SkirkCunningHelper.tryMarkPickableGrant(gameEntity2.getId())) {
                return true;
            }
            SkirkCunningBridge.applyDelta(player, (EntityAvatar)gameEntity2, f2);
            return true;
        }
        if (SkirkCunningHelper.tryDebouncedSkillGain(gameEntity2, f2)) {
            ActionAddSpecialEnergy.syncMavuikaBurst(gameEntity2);
            return true;
        }
        SpecialEnergyBarHelper.ensureAndSync(gameEntity2);
        gameEntity2.addSpecialEnergy(f2);
        SpecialEnergyBarHelper.ensureAndSync(gameEntity2);
        ActionAddSpecialEnergy.syncMavuikaBurst(gameEntity2);
        return true;
    }

    private static boolean isExtraEnergyRatio(DynamicFloat dynamicFloat) {
        if (dynamicFloat == null || !dynamicFloat.isDynamic()) {
            return false;
        }
        try {
            List<DynamicFloat.StackOp> list = dynamicFloat.getOps();
            if (list == null) {
                return false;
            }
            for (DynamicFloat.StackOp stackOp : list) {
                if (stackOp == null || stackOp.sValue == null || !"SkirkNew_Constellation_2_ExtraEnergy".equals(stackOp.sValue)) continue;
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    private static void syncMavuikaBurst(GameEntity gameEntity) {
        float f = gameEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY);
        Map<String, Float> map = gameEntity.getGlobalAbilityValues();
        map.put(BURST_ENERGY_KEY, Float.valueOf(f));
        gameEntity.onAbilityValueUpdate();
        if (gameEntity.getScene() != null && gameEntity.getScene().getHost() != null) {
            gameEntity.getScene().getHost().sendPacket(new PacketServerGlobalValueChangeNotify(gameEntity, BURST_ENERGY_KEY, f));
        }
    }
}
