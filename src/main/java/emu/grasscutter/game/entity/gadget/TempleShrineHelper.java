package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.scripts.constants.ScriptGadgetState;

/**
 * Shrine of Depths: a gate ({@code SceneObj_Gear_Gate_Temple01}) with a locked chest behind it.
 *
 * <p>The group script already implements the whole flow — {@code scene3_group133003505.lua} for
 * Mondstadt is the reference:
 *
 * <pre>
 *   condition_EVENT_GADGET_STATE_CHANGE_505005:
 *       evt.param2 == 505001 and evt.param1 == GadgetState.GearStart
 *   action_EVENT_GADGET_STATE_CHANGE_505005:
 *       SetGadgetStateByConfigId(505003, GadgetState.Default)  -- unlock the chest
 *       KillEntityByConfigId(505001)                           -- remove the gate
 * </pre>
 *
 * <p>So the server only has to do two things: consume one key, and move the gate to
 * {@link ScriptGadgetState#GearStart}. Everything else — unlocking the chest, removing the gate,
 * persisting the opened state — is already handled by the script and by the existing gadget-state
 * plumbing.
 *
 * <p>The required key is not hard-coded per group. It is derived from the sibling chest's
 * {@code drop_tag}, which the group data already carries.
 */
public final class TempleShrineHelper {
    private TempleShrineHelper() {}

    private static final String GATE_JSON_NAME = "SceneObj_Gear_Gate_Temple01";

    /**
     * Shrine key item ids.
     *
     * <p>Only {@link #KEY_MONDSTADT} has been verified in game (see the pull request). The other
     * three are the contiguous ids of the same item family ({@code rank = 410011},
     * {@code MATERIAL_CONSUME}); they are wired up so the other regions can be tested, but they have
     * not been pressed against a real gate yet.
     */
    private static final int KEY_MONDSTADT = 107006;

    private static final int KEY_LIYUE = 107007;
    private static final int KEY_INAZUMA = 107008;
    private static final int KEY_SUMERU = 107018;

    /**
     * Maps a chest {@code drop_tag} to the key item the gate consumes.
     *
     * <p>The {@code drop_tag} values are Chinese in the resource data regardless of locale, so they
     * are matched here as-is.
     *
     * @return the key item id, or {@code 0} when the tag is not a shrine tag
     */
    private static int keyForTag(String dropTag) {
        if (dropTag == null) {
            return 0;
        }
        if (dropTag.contains("蒙德")) {
            return KEY_MONDSTADT;
        }
        if (dropTag.contains("璃月")) {
            return KEY_LIYUE;
        }
        if (dropTag.contains("稻妻")) {
            return KEY_INAZUMA;
        }
        if (dropTag.contains("须弥")) {
            return KEY_SUMERU;
        }
        return 0;
    }

    public static boolean isTempleGate(EntityGadget gadget) {
        var data = gadget.getGadgetData();
        return data != null && GATE_JSON_NAME.equals(data.getJsonName());
    }

    /**
     * Handles an interaction with a Shrine gate.
     *
     * @return {@code true} when the interaction was ours to handle — including the "no key, refused"
     *     case, because the client is waiting for a response either way. {@code false} lets
     *     {@code GadgetObject.onInteract} keep its original behaviour.
     */
    public static boolean onInteract(EntityGadget gate, Player player) {
        if (!isTempleGate(gate)) {
            return false;
        }

        int keyId = resolveKeyId(gate);
        if (keyId == 0) {
            Grasscutter.getLogger()
                    .info(
                            "[Temple] group {} has no shrine key mapping, falling back to the old path",
                            gate.getGroupId());
            return false;
        }

        int held = player.getInventory().getItemCountById(keyId);
        if (held <= 0) {
            Grasscutter.getLogger()
                    .info(
                            "[Temple] uid {} has no shrine key {} (group {}, config {}) -> refused",
                            player.getUid(),
                            keyId,
                            gate.getGroupId(),
                            gate.getConfigId());
            return true;
        }

        if (!player.getInventory().removeItemById(keyId, 1)) {
            Grasscutter.getLogger()
                    .info("[Temple] uid {} failed to remove shrine key {}", player.getUid(), keyId);
            return true;
        }

        Grasscutter.getLogger()
                .info(
                        "[Temple] uid {} consumed shrine key {} x1 ({} left) -> gate to GearStart"
                                + " (group {}, config {})",
                        player.getUid(),
                        keyId,
                        held - 1,
                        gate.getGroupId(),
                        gate.getConfigId());
        gate.updateState(ScriptGadgetState.GearStart);
        return true;
    }

    /** The key is defined by the sibling chest whose {@code drop_tag} starts with {@code 灵龛}. */
    private static int resolveKeyId(EntityGadget gate) {
        var scene = gate.getScene();
        if (scene == null) {
            return 0;
        }
        var scriptManager = scene.getScriptManager();
        if (scriptManager == null) {
            return 0;
        }
        var group = scriptManager.getGroupById(gate.getGroupId());
        if (group == null || group.gadgets == null) {
            return 0;
        }
        for (var meta : group.gadgets.values()) {
            if (meta == null || meta.drop_tag == null || !meta.drop_tag.startsWith("灵龛")) {
                continue;
            }
            int keyId = keyForTag(meta.drop_tag);
            if (keyId != 0) {
                return keyId;
            }
        }
        return 0;
    }
}
