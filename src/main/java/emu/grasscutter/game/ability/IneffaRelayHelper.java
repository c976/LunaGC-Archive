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
 * Ineffa's Volti-tower (Relay) is client-owned via {@code EvtCreateGadgetNotify}.
 *
 * <p>A server {@link EntityGadget} copy is an empty shell the caster can see. Those shells stack with
 * the real client summon (and with each other when replace misses), which matches the reported
 * multi-summon / leftover-hat bugs. Duration cleanup also never runs server {@code onRemoved}, so
 * shells outlive the 20s field time.
 */
public final class IneffaRelayHelper {
    public static final int INEFFA_AVATAR_ID = 10000116;
    public static final int RELAY_GADGET_ID = 41116005;
    public static final int RELAY_HIT_GADGET_ID = 41116006;
    public static final int RELAY_MOVE_ASIST_GADGET_ID = 41116007;
    public static final int RELAY_MOVE_ASIST_01_GADGET_ID = 41116008;
    public static final int BURST_BULLET_GADGET_ID = 41116009;
    public static final int BURST_BULLET_T2_GADGET_ID = 41116010;
    public static final int BURST_BULLET_T2_01_GADGET_ID = 41116011;

    private IneffaRelayHelper() {}

    /** SkillObj gadgets Ineffa creates; client owns the chain. */
    public static boolean isIneffaClientSkillObj(int gadgetId) {
        return gadgetId == RELAY_GADGET_ID
                || gadgetId == RELAY_HIT_GADGET_ID
                || gadgetId == RELAY_MOVE_ASIST_GADGET_ID
                || gadgetId == RELAY_MOVE_ASIST_01_GADGET_ID
                || gadgetId == BURST_BULLET_GADGET_ID
                || gadgetId == BURST_BULLET_T2_GADGET_ID
                || gadgetId == BURST_BULLET_T2_01_GADGET_ID;
    }

    /** Field pieces that must stay at most one Relay (+ its assists). */
    public static boolean isIneffaRelayFamily(int gadgetId) {
        return gadgetId == RELAY_GADGET_ID
                || gadgetId == RELAY_HIT_GADGET_ID
                || gadgetId == RELAY_MOVE_ASIST_GADGET_ID
                || gadgetId == RELAY_MOVE_ASIST_01_GADGET_ID;
    }

    /**
     * Drop server shells for Ineffa skill objs owned by this player's avatars.
     *
     * @param onlyRelayFamily when true, leave burst bullets alone
     */
    public static void purgeServerShells(Player player, boolean onlyRelayFamily) {
        if (player == null || player.getScene() == null || player.getTeamManager() == null) {
            return;
        }

        var scene = player.getScene();
        Set<Integer> avatarEntityIds = avatarEntityIds(player);

        for (GameEntity entity : new ArrayList<>(scene.getEntities().values())) {
            if (!(entity instanceof EntityGadget gadget) || entity instanceof EntityClientGadget) {
                continue;
            }
            int gadgetId = gadget.getGadgetId();
            if (onlyRelayFamily) {
                if (!isIneffaRelayFamily(gadgetId)) continue;
            } else if (!isIneffaClientSkillObj(gadgetId)) {
                continue;
            }
            if (!ownedByPlayer(gadget.getOwner(), player, avatarEntityIds)) {
                continue;
            }
            scene.removeEntity(gadget);
            Grasscutter.getLogger()
                    .debug(
                            "[IneffaRelay] uid={} purged server shell gadget={} entity={}",
                            player.getUid(),
                            gadgetId,
                            gadget.getId());
        }
    }

    /**
     * Keep at most one client Relay (and wipe leftover assists) when a new client Relay appears.
     * Refresh casts do not create a new EvtCreateGadget; only a true re-summon does.
     */
    public static void onClientRelayCreated(Player player, EntityClientGadget created) {
        if (player == null || created == null || created.getGadgetId() != RELAY_GADGET_ID) {
            return;
        }

        purgeServerShells(player, true);

        var scene = player.getScene();
        if (scene == null) return;

        for (GameEntity entity : new ArrayList<>(scene.getEntities().values())) {
            if (!(entity instanceof EntityClientGadget gadget) || gadget == created) {
                continue;
            }
            if (gadget.getOwner() != player) {
                continue;
            }
            int gadgetId = gadget.getGadgetId();
            if (!isIneffaRelayFamily(gadgetId)) {
                continue;
            }
            // A prior Relay (or orphaned assist/hat) from an earlier cast.
            scene.onPlayerDestroyGadget(gadget.getId());
            Grasscutter.getLogger()
                    .debug(
                            "[IneffaRelay] uid={} removed stale client gadget={} entity={}",
                            player.getUid(),
                            gadgetId,
                            gadget.getId());
        }
    }

    /** When the client removes its Relay, also drop any server shells / orphaned assists. */
    public static void onClientGadgetDestroyed(Player player, int entityId, int gadgetId) {
        if (player == null || !isIneffaRelayFamily(gadgetId)) {
            return;
        }
        purgeServerShells(player, true);
        if (gadgetId != RELAY_GADGET_ID) {
            return;
        }

        var scene = player.getScene();
        if (scene == null) return;

        for (GameEntity entity : new ArrayList<>(scene.getEntities().values())) {
            if (!(entity instanceof EntityClientGadget gadget) || gadget.getId() == entityId) {
                continue;
            }
            if (gadget.getOwner() != player) {
                continue;
            }
            int id = gadget.getGadgetId();
            if (id != RELAY_HIT_GADGET_ID
                    && id != RELAY_MOVE_ASIST_GADGET_ID
                    && id != RELAY_MOVE_ASIST_01_GADGET_ID) {
                continue;
            }
            scene.onPlayerDestroyGadget(gadget.getId());
        }
    }

    private static Set<Integer> avatarEntityIds(Player player) {
        Set<Integer> ids = new HashSet<>();
        for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
            if (avatar != null) {
                ids.add(avatar.getId());
            }
        }
        return ids;
    }

    private static boolean ownedByPlayer(
            GameEntity owner, Player player, Set<Integer> avatarEntityIds) {
        if (owner == null) {
            return false;
        }
        if (avatarEntityIds.contains(owner.getId())) {
            return true;
        }
        return owner instanceof EntityAvatar avatar && avatar.getPlayer() == player;
    }
}
