package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.ability.SkirkCunningHelper;
import emu.grasscutter.game.props.EntityIdType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.managers.stamina.Consumption;
import emu.grasscutter.game.managers.stamina.ConsumptionType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.game.managers.stamina.StaminaManager;
import emu.grasscutter.net.proto.ChangeHpDebtsReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.packet.send.PacketEntityFightPropChangeReasonNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.quest.enums.QuestContent;
import static emu.grasscutter.config.Configuration.GAME_OPTIONS;
import java.util.concurrent.ConcurrentHashMap;

@AbilityAction(AbilityModifierAction.Type.AvatarSkillStart)
public class ActionAvatarSkillStart extends AbilityActionHandler {
    private static final ConcurrentHashMap<Integer, Long> lastSkirkBurstMs = new ConcurrentHashMap<>();

    public static void clearPlayerState(emu.grasscutter.game.player.Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        for (EntityAvatar entityAvatar : new java.util.ArrayList<>(player.getTeamManager().getActiveTeam())) {
            if (entityAvatar != null) {
                lastSkirkBurstMs.remove(entityAvatar.getId());
            }
        }
    }

    public static void clearEntityState(int entityId) {
        lastSkirkBurstMs.remove(entityId);
    }

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        var owner = ability.getOwner();
        float costStaminaRatio = action.costStaminaRatio.get(ability);

        if (costStaminaRatio != 1.0f && costStaminaRatio != 0.0f) {
            var player = ability.getPlayerOwner();
            if (player != null) {
                StaminaManager staminaManager = player.getStaminaManager();
                GameSession session = player.getSession();

                int staminaCost = (int) (costStaminaRatio * 100);

                Consumption consumption = new Consumption(
                    ConsumptionType.FIGHT,
                    -Math.abs(staminaCost)
                );

                staminaManager.updateStaminaRelative(session, consumption, true);
                staminaManager.staminaRecoverDelay = 0;
            }
        }

       if (action.skillID == 11065) {
            Avatar avatar = ability.getPlayerOwner().getCurrentAvatar();
            if (GAME_OPTIONS.energyUsage) {
                avatar.clearSpecialEnergy();
            } else {

                avatar.addSpecialEnergy(avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
            }
        } else if (action.skillID == 11145 || action.skillID == 11147) {
            int entityId = owner == null ? 0 : owner.getId();
            long now = System.currentTimeMillis();
            Long previous = lastSkirkBurstMs.get(entityId);
            if (previous == null || now - previous >= 400L) {
                lastSkirkBurstMs.put(entityId, now);
                SkirkCunningHelper.onBurstSkillStart(ability.getPlayerOwner(), action.skillID, owner);
            } else {
                Grasscutter.getLogger().debug("SkirkCunning: skip duplicate AvatarSkillStart {}", action.skillID);
            }
        }
        if (owner instanceof EntityAvatar avatar) {
            avatar
                    .getPlayer()
                    .getQuestManager()
                    .queueEvent(QuestContent.QUEST_CONTENT_SKILL, action.skillID);
            try {
                // Secondary confirm: some bursts hit AvatarSkillStart before/without the
                // invuln-modifier path in AbilityManager.onPossibleElementalBurst.
                avatar.getPlayer().getEnergyManager().confirmBurstCast(avatar.getAvatar(), action.skillID);
            } catch (Throwable ignored) {
                // Energy confirm must not cancel skill start.
            }
        } else {
            Grasscutter.getLogger()
                    .warn("AvatarSkillStart not implemented for other entities than EntityAvatar right now");
            return false;
        }

        return true;
    }
}
