package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import java.util.Arrays;

@AbilityAction(AbilityModifierAction.Type.KillSelf)
public final class ActionKillSelf extends AbilityActionHandler {
    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (target == null) {
            Grasscutter.getLogger().warn("Tried killing null target");
            return false;
        }

        if (target instanceof EntityAvatar) {
            return true;
        }

        if (action.configIdArray != null && action.configIdArray.length > 0) {
            if (!(target instanceof EntityBaseGadget gadget)
                    || Arrays.stream(action.configIdArray).noneMatch(id -> id == gadget.getGadgetId())) {
                return true;
            }
        }

        // killSelfType "Disappear" is the entity fading out or flying away - a bird leaving when the
        // player walks up to it, a gadget whose disappear timer ran out. Going through killEntity here
        // reported a death the player never caused: LifeStateChangeNotify(LIFE_DEAD), the creature's
        // killDrop on the ground (crane meat and raw meat raining down from birds in flight), and the
        // spawn recorded as consumed, so the wildlife stayed gone for the whole respawn interval.
        // The client already played the disappear itself, so all that is left is taking the entity out.
        if (isDisappearKillSelf(action)) {
            Grasscutter.getLogger()
                    .debug(
                            "ActionKillSelf: removing entity {} ({}) for killSelfType=Disappear without death",
                            target.getId(),
                            target.getEntityTypeId());
            target.getScene().removeEntity(target, VisionType.VisionType_VISION_REMOVE);
            return true;
        }

        target.getScene().killEntity(target);
        return true;
    }

    private static boolean isDisappearKillSelf(AbilityModifierAction action) {
        return action.killSelfType != null && "Disappear".equalsIgnoreCase(action.killSelfType.trim());
    }
}
