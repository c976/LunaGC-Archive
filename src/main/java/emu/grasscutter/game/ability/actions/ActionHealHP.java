package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.ability.ArlecchinoBurstBoL;
import emu.grasscutter.game.ability.ArlecchinoBoLUtil;
import emu.grasscutter.game.ability.ClorindeBoLUtil;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.*;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketEvtBeingHealedNotify;
import emu.grasscutter.game.props.FightProperty;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

@AbilityAction(AbilityModifierAction.Type.HealHP)
public final class ActionHealHP extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (ClorindeBoLUtil.handleHealHp(ability, action, target)) return true;
        var owner = resolveHealCaster(ability);

        if (owner == null) return false;

        var properties = new Object2FloatOpenHashMap<String>();

        // Globals first: HealHP formulas often MUL ability specials with keys written by
        // GetFightProperty / SetGlobalValue (e.g. Lauma C1 _ABILITY_Lauma_Constellation_1_Mastery).
        owner.getGlobalAbilityValues().forEach(properties::put);
        if (target != null && target != owner) {
            target.getGlobalAbilityValues().forEach(properties::put);
        }

        for (var property : FightProperty.values()) {
            var name = property.name();
            var value = owner.getFightProperty(property);
            properties.put(name, value);
        }

        for (var e : ability.getAbilitySpecials().object2FloatEntrySet()) {
            properties.put(e.getKey(), e.getFloatValue());
        }

        var amountByCasterMaxHPRatio = action.amountByCasterMaxHPRatio.get(properties, 0);
        var amountByCasterAttackRatio = action.amountByCasterAttackRatio.get(properties, 0);
        var amountByCasterCurrentHPRatio = action.amountByCasterCurrentHPRatio.get(properties, 0);
        var amountByCasterDefRatio = action.amountByCasterDefRatio.get(properties, 0);
        var amountByTargetCurrentHPRatio = action.amountByTargetCurrentHPRatio.get(properties, 0);
        var amountByTargetMaxHPRatio = action.amountByTargetMaxHPRatio.get(properties, 0);
        var amountToRegenerate = action.amount.get(properties, 0);

        amountToRegenerate +=
                amountByCasterMaxHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
        amountToRegenerate +=
                amountByCasterAttackRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_ATTACK);
        amountToRegenerate +=
                amountByCasterCurrentHPRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        amountToRegenerate +=
                amountByCasterDefRatio * owner.getFightProperty(FightProperty.FIGHT_PROP_CUR_DEFENSE);

        // otherTargets may retarget (e.g. Lauma C1 SelectTargetsByShape + CurLocalAvatar).
        GameEntity healTarget = resolveHealTarget(ability, action, target);
        if (healTarget == null) return false;

        var abilityRatio = 1.0f;
        if (!action.ignoreAbilityProperty)
            abilityRatio +=
                    owner.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD)
                            + healTarget.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD);

        amountToRegenerate +=
                amountByTargetCurrentHPRatio * healTarget.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
        amountToRegenerate +=
                amountByTargetMaxHPRatio * healTarget.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

        String healTag = action.healTag;
        // Do NOT set Dodge_HealFlag=1 here: the enhanced full-BoL Impale requires HealFlag==0 at the start.
        // Setting it to 1 makes High fail without falling back to Medium, which shows up as full BoL
        // sometimes not enhancing.
        // Officially it is set to 1 only at the end of the successful High branch, and cleared back to 0 by
        // onBeingHealed.

        String abilityName =
                ability.getData() != null && ability.getData().abilityName != null
                        ? ability.getData().abilityName
                        : "";
        // Clorinde: ForbidFoodHeal makes isConvertToHpDebt() always true. The old x0.8 path that paid down
        // BoL without restoring HP has been removed.
        // Impale and Night Vigil conversion are handled by handleHealHp at the top of the file; this is a
        // second attempt for anything that slipped through.
        if (target.isConvertToHpDebt()
                && target instanceof EntityAvatar debtAvatar
                && debtAvatar.getAvatar() != null
                && debtAvatar.getAvatar().getAvatarId() == 10000098
                && ClorindeBoLUtil.handleHealHp(ability, action, target)) {
            return true;
        }

        if ("MizukiBurstSelf".equals(healTag)) {
            amountToRegenerate *= 2.0f;
            Grasscutter.getLogger().debug("Healing increased by 100% for target {}", target);
        }

        float finalAmount = amountToRegenerate * abilityRatio * action.healRatio.get(ability, 1f);

        if (!Float.isFinite(finalAmount)) {
            Grasscutter.getLogger().warn("Rejected non-finite HealHP formula: ability={} caster={} target={} amount={}",
                    abilityName, owner.getId(), healTarget.getId(), finalAmount);
            return false;
        }

        if (healTarget instanceof EntityAvatar burstHealer
                && burstHealer.getAvatar() != null
                && burstHealer.getAvatar().getAvatarId() == ArlecchinoBoLUtil.ARLECCHINO_AVATAR_ID
                && (ArlecchinoBoLUtil.BURST_HEAL_TAG.equals(healTag)
                        || (abilityName.contains("Arlecchino")
                                && abilityName.contains("ElementalBurst")))) {
            // Preserve the cast's Bond until its damage settles; the payout restores HP directly.
            synchronized (burstHealer) {
                if (!ArlecchinoBurstBoL.prepareBurstHeal(
                        burstHealer, finalAmount, action.muteHealEffect)) {
                    ArlecchinoBoLUtil.applyBurstHeal(
                            burstHealer, finalAmount, action.muteHealEffect);
                }
            }
        } else {
            float realHeal = healTarget.heal(finalAmount, action.muteHealEffect);
            if (realHeal > 0 && !action.muteHealEffect) {
                healTarget.getWorld().broadcastPacket(new PacketEvtBeingHealedNotify(owner, healTarget, finalAmount, realHeal));
            }
        }

        if (finalAmount > 0) {
            var healOwner = owner;
            for (var mod : healOwner.getInstancedModifiers().values()) {
                var modData = mod.getModifierData();
                var modAbility = mod.getAbility();
                if (modData != null && modData.onHeal != null && modAbility != null) {
                    for (var healEvt : modData.onHeal) {
                        ability.getManager().executeAction(modAbility, healEvt, ByteString.EMPTY, healOwner);
                    }
                }
            }
        }

        if (finalAmount > 0
                && "Avatar_Furina_Constellation_2".equals(ability.getData().abilityName)
                && healTarget instanceof EntityAvatar furinaTarget
                && furinaTarget.getAvatar().getAvatarId() == 10000089) {
            var player = ability.getPlayerOwner();
            if (player != null) {
                float baseAmount = finalAmount / abilityRatio;
                var seen = new java.util.HashSet<Long>();
                seen.add(furinaTarget.getAvatar().getGuid());
                for (var member : player.getTeamManager().getActiveTeam()) {
                    if (!seen.add(member.getAvatar().getGuid())) continue;
                    if (member.isConvertToHpDebt()) continue;
                    float memberRatio = 1.0f;
                    if (!action.ignoreAbilityProperty)
                        memberRatio += owner.getFightProperty(FightProperty.FIGHT_PROP_HEAL_ADD)
                            + member.getFightProperty(FightProperty.FIGHT_PROP_HEALED_ADD);
                    float spreadAmount = baseAmount * memberRatio;
                    float spreadReal = member.heal(spreadAmount, action.muteHealEffect);
                    if (spreadReal > 0 && !action.muteHealEffect) {
                        member.getWorld().broadcastPacket(new PacketEvtBeingHealedNotify(owner, member, spreadAmount, spreadReal));
                    }
                }
            }
        }

        return true;
    }

    /** The entity whose healing bonus applies: a gadget's owner, else the named caster. */
    private static GameEntity resolveHealCaster(Ability ability) {
        var owner = ability.getOwner();
        // Gadgets may be spawned by gadgets (Furina's salon members); follow a few levels.
        for (int depth = 0; depth < 4 && owner instanceof EntityClientGadget gadget; depth++) {
            var scene = gadget.getScene();
            if (scene == null) return null;
            owner = scene.getEntityById(gadget.getOwnerEntityId());
            if (owner == null) owner = scene.getEntityById(gadget.getOriginalOwnerEntityId());
            if (owner == null
                    && (gadget.gadgetId == 41089011 || gadget.gadgetId == 41089012 || gadget.gadgetId == 41089013)
                    && ability.getPlayerOwner() != null) {
                owner = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
            }
            if (!(owner instanceof EntityClientGadget)) return owner;
        }
        if (!(owner instanceof EntityAvatar)) return owner;
        var caster = ability.getCasterEntity();
        return caster != null ? caster : owner;
    }

    /**
     * When HealHP names {@code otherTargets} with base {@code CurLocalAvatar}, heal the on-field
     * character instead of Self (off-field casters like Lauma C1).
     */
    private static GameEntity resolveHealTarget(
            Ability ability, AbilityModifierAction action, GameEntity fallback) {
        if (action.otherTargets == null || ability.getPlayerOwner() == null) {
            return fallback;
        }
        Object base = action.otherTargets.get("AALABPOFJDA");
        if (base == null) {
            base = action.otherTargets.get("base");
        }
        if (!"CurLocalAvatar".equals(String.valueOf(base))
                && !"OriginOwner".equals(String.valueOf(base))) {
            return fallback;
        }
        var current = ability.getPlayerOwner().getTeamManager().getCurrentAvatarEntity();
        return current != null ? current : fallback;
    }
}
