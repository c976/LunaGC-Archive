package emu.grasscutter.game.ability.actions;

import com.google.protobuf.ByteString;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.game.ability.Ability;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.world.Position;
import java.util.Map;

/**
 * Writes an ability's global <em>position</em>.
 *
 * <p>These keys are what a summon's born block reads back: the Perpetual Mechanical Array's split
 * writes {@code SplitPos1..4} as the boss's own position plus a small offset, and each minion's
 * Summon then asks for one of them by name. Without this handler the keys are never written, and the
 * only position left for the summon is the one packed in the notify - which is empty, because proto3
 * does not put zero-valued fields on the wire - so every minion spawned at the world origin,
 * thousands of units from the boss.
 *
 * <p>Only {@code ConfigBornBySelf} is resolved, which is what the world bosses use. Any other born
 * type is logged and skipped rather than guessed at.
 */
@AbilityAction(AbilityModifierAction.Type.SetGlobalPos)
public final class ActionSetGlobalPos extends AbilityActionHandler {

    @Override
    public boolean execute(
            Ability ability, AbilityModifierAction action, ByteString abilityData, GameEntity target) {
        if (target == null || target.getScene() == null) return true;
        if (action.key == null) return true;

        Map<String, Object> born = action.born;
        if (born == null) return true;

        String type = String.valueOf(born.get("$type"));
        if (!"ConfigBornBySelf".equals(type)) {
            Grasscutter.getLogger()
                    .debug("[SetGlobalPos] unsupported born {} for key {}", type, action.key);
            return true;
        }

        Position pos = new Position(target.getPosition());
        applyOffset(pos, born.get("offset"), Boolean.TRUE.equals(born.get("onGround")));
        target.getGlobalAbilityPositions().put(action.key, pos);
        Grasscutter.getLogger().debug("[SetGlobalPos] {} = {} (born {})", action.key, pos, type);
        return true;
    }

    /**
     * Shift the caster's position by the born block's offset.
     *
     * <p>With {@code onGround} the vertical component is taken as lateral rather than as height. The
     * Perpetual Mechanical Array's four split points differ only by the sign of {@code y}, so reading
     * it as height collapsed them into two pairs stacked on the same ground point, and the two with a
     * negative offset spawned inside the terrain - below the surface the server has no floor to catch
     * them, so they fell until {@code die_y} culled them and the player saw half the split vanish.
     * Read as lateral the four become the corners of a square around the caster, which is what the
     * fight looks like, and the caster's own height is the ground the client snaps them to.
     */
    @SuppressWarnings("unchecked")
    private static void applyOffset(Position pos, Object offset, boolean onGround) {
        if (!(offset instanceof Map)) return;
        Map<String, Object> off = (Map<String, Object>) offset;
        float dy = asFloat(off.get("y"));
        pos.setX(pos.getX() + asFloat(off.get("x")));
        pos.setZ(pos.getZ() + asFloat(off.get("z")));
        if (onGround) {
            pos.setX(pos.getX() + dy);
        } else {
            pos.setY(pos.getY() + dy);
        }
    }

    private static float asFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : 0f;
    }
}
