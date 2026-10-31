package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Venti SkillObj gadgets are client-owned via {@code EvtCreateGadgetNotify}.
 *
 * <p>A server {@link EntityGadget} copy is an empty shell the caster can see. For Stormeye that
 * shell usually lands under the avatar (modifier {@code onAdded} has no pos payload). For WindBlade
 * the same pattern shows up as an E-skill field at the feet while Hexenzirkel-infused NAs fire.
 */
public final class VentiSkillObjHelper {
    public static final int HURRICANE_ARROW = 41022001;
    public static final int WIND_BLADE = 41022002;
    public static final int WIND_BLADE_LAND = 41022003;
    public static final int WIND_FIELD = 41022014;

    private VentiSkillObjHelper() {}

    public static boolean isVentiClientSkillObj(int gadgetId) {
        return gadgetId == HURRICANE_ARROW
                || gadgetId == WIND_BLADE
                || gadgetId == WIND_BLADE_LAND
                || gadgetId == WIND_FIELD;
    }

    /** Drop server shells for Venti skill objs owned by this player's avatars. */
    public static void purgeServerShells(Player player) {
        if (player == null || player.getScene() == null || player.getTeamManager() == null) {
            return;
        }

        var scene = player.getScene();
        Set<Integer> avatarEntityIds = new HashSet<>();
        for (var avatarEntity : player.getTeamManager().getActiveTeam()) {
            if (avatarEntity != null) {
                avatarEntityIds.add(avatarEntity.getId());
            }
        }

        for (GameEntity entity : new ArrayList<>(scene.getEntities().values())) {
            if (!(entity instanceof EntityGadget gadget) || entity instanceof EntityClientGadget) {
                continue;
            }
            if (!isVentiClientSkillObj(gadget.getGadgetId())) {
                continue;
            }
            if (!ownedByPlayer(gadget.getOwner(), avatarEntityIds)) {
                continue;
            }
            scene.removeEntity(gadget);
            Grasscutter.getLogger()
                    .debug(
                            "[VentiSkillObj] uid={} purged server shell gadget={} entity={}",
                            player.getUid(),
                            gadget.getGadgetId(),
                            gadget.getId());
        }
    }

    public static void onClientSkillObjCreated(Player player, EntityClientGadget created) {
        if (player == null || created == null || !isVentiClientSkillObj(created.getGadgetId())) {
            return;
        }
        purgeServerShells(player);
    }

    private static boolean ownedByPlayer(GameEntity owner, Set<Integer> avatarEntityIds) {
        if (owner == null) {
            return false;
        }
        if (owner instanceof EntityAvatar) {
            return avatarEntityIds.contains(owner.getId());
        }
        return avatarEntityIds.contains(owner.getId());
    }
}
