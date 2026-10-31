package emu.grasscutter.game.ability;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.world.Position;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Resolves an ability action's {@code otherTargets} block into the entities it points at.
 *
 * <p>{@code SelectTargetsByShape} is what avatar summons use to look for something to shoot at.
 * {@code SelectTargetsByChildren} finds gadgets this entity owns (e.g. Ineffa's existing Relay).
 * Anything else returns null, meaning "no selector" - the caller then keeps whatever target it
 * already had.
 */
public final class AbilityTargetSelector {
    /** Shape names encode their size, e.g. CircleR20H10 is a radius of 20 and a height of 10. */
    private static final Pattern RADIUS = Pattern.compile("R(\\d+(?:\\.\\d+)?)");

    private static final Pattern HEIGHT = Pattern.compile("H(\\d+(?:\\.\\d+)?)");

    private AbilityTargetSelector() {}

    public static List<GameEntity> select(
            Map<String, Object> otherTargets, Ability ability, GameEntity self) {
        if (otherTargets == null || self == null) return null;

        var type = otherTargets.get("$type");
        if ("SelectTargetsByChildren".equals(type)) {
            return selectByChildren(otherTargets, ability, self);
        }
        if (!"SelectTargetsByShape".equals(type)) return null;

        var scene = self.getScene();
        if (scene == null) return null;

        var wanted = entityTypes(otherTargets);
        var shape = String.valueOf(otherTargets.getOrDefault("shapeName", ""));
        float radius = dimension(RADIUS, shape);
        float height = dimension(HEIGHT, shape);
        var camp = String.valueOf(otherTargets.getOrDefault("campTargetType", "Enemy"));
        var origin = self.getPosition();

        var found = new ArrayList<GameEntity>();
        for (var entity : scene.getEntities().values()) {
            if (entity == self || !entity.isAlive()) continue;
            if (!wanted.isEmpty() && !isOfType(entity, wanted)) continue;
            if (!matchesCamp(entity, camp, ability)) continue;
            if (radius > 0 && horizontalDistance(origin, entity.getPosition()) > radius) continue;
            if (height > 0 && Math.abs(origin.getY() - entity.getPosition().getY()) > height) continue;

            found.add(entity);
        }

        if ("Nearest".equals(otherTargets.get("sortType"))) {
            found.sort(Comparator.comparingDouble(e -> origin.computeDistance(e.getPosition())));
        }

        int limit = asInt(otherTargets.get("topLimit"));
        return limit > 0 && found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /**
     * Gadgets owned by {@code self} whose config/gadget id matches {@code configID}.
     *
     * <p>Returns an empty list (not null) when nothing matches, so Predicated takes failActions
     * instead of wrongly testing predicates against the caster.
     */
    private static List<GameEntity> selectByChildren(
            Map<String, Object> otherTargets, Ability ability, GameEntity self) {
        var scene = self.getScene();
        if (scene == null) return List.of();

        int configId = asInt(otherTargets.get("configID"));
        var found = new ArrayList<GameEntity>();

        if (self instanceof EntityGadget parent && parent.getChildren() != null) {
            for (var child : parent.getChildren()) {
                if (child == null || !child.isAlive()) continue;
                if (configId > 0 && child.getEntityTypeId() != configId) continue;
                found.add(child);
            }
        }

        var player = ability != null ? ability.getPlayerOwner() : null;
        for (var entity : scene.getEntities().values()) {
            if (entity == self || !entity.isAlive()) continue;
            if (configId > 0 && entity.getEntityTypeId() != configId) continue;

            boolean owned = false;
            if (entity instanceof EntityGadget gadget && !(entity instanceof EntityClientGadget)) {
                owned = gadget.getOwner() == self;
            } else if (entity instanceof EntityClientGadget clientGadget) {
                owned =
                        clientGadget.getOwnerEntityId() == self.getId()
                                || clientGadget.getOriginalOwnerEntityId() == self.getId()
                                || (player != null
                                        && clientGadget.getOwner() == player
                                        && self instanceof EntityAvatar avatar
                                        && avatar.getPlayer() == player);
            }
            if (!owned) continue;
            if (!found.contains(entity)) {
                found.add(entity);
            }
        }

        int limit = asInt(otherTargets.get("topLimit"));
        return limit > 0 && found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /** The names come from either entityTypes or entityTypePriority depending on the selector. */
    private static Set<String> entityTypes(Map<String, Object> otherTargets) {
        var names = new HashSet<String>();
        for (var key : new String[] {"entityTypes", "entityTypePriority"}) {
            if (otherTargets.get(key) instanceof List<?> list) {
                list.forEach(name -> names.add(String.valueOf(name)));
            }
        }
        return names;
    }

    private static boolean isOfType(GameEntity entity, Set<String> wanted) {
        for (var name : wanted) {
            var matches =
                    switch (name) {
                        case "Monster" -> entity instanceof EntityMonster;
                        case "Gadget" -> entity instanceof EntityBaseGadget;
                        case "Avatar", "RemoteAvatar" -> entity instanceof EntityAvatar;
                        case "Vehicle" -> entity instanceof EntityVehicle;
                        default -> false;
                    };
            if (matches) return true;
        }
        return false;
    }

    /**
     * Camps are only approximated: the caster's own avatars and the gadgets they spawned are not
     * enemies, and everything else in the scene is. That is enough to tell "is there something to
     * shoot at nearby" apart from "I am looking at my own bullets".
     */
    private static boolean matchesCamp(GameEntity entity, String camp, Ability ability) {
        var player = ability != null ? ability.getPlayerOwner() : null;
        boolean own =
                entity instanceof EntityAvatar avatar && avatar.getPlayer() == player
                        || entity instanceof EntityClientGadget gadget && gadget.getOwner() == player;

        return switch (camp) {
            case "Enemy" -> !own;
            case "Alliance", "SelfCamp" -> own;
            default -> true;
        };
    }

    private static float horizontalDistance(Position a, Position b) {
        var dx = a.getX() - b.getX();
        var dz = a.getZ() - b.getZ();
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float dimension(Pattern pattern, String shapeName) {
        var matcher = pattern.matcher(shapeName);
        return matcher.find() ? Float.parseFloat(matcher.group(1)) : 0f;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
