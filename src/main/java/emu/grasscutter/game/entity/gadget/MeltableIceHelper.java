package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.config.Configuration;
import emu.grasscutter.game.entity.EntityGadget;
import java.util.Set;

/**
 * Snezhnaya's fire-meltable ice (碎冰): ice that shrinks under Fire hits until it shatters, and then
 * freezes back.
 *
 * <p>The melting itself is client-side ability logic driven by the gadget's own server global values
 * (see {@code EntityGadget.initialAbilityInfo}); once the ice is small enough the client kills it. The
 * refreeze belongs to the official group scripts, but the 7.1 resources only carry spawn data for these
 * groups, so the server re-creates the ice itself a short while after it shatters.
 */
public final class MeltableIceHelper {
    /** The world variants a Fire hit shatters. The heat-melted puzzle blocks stay gone. */
    private static final Set<String> REFREEZING_ICE =
            Set.of(
                    "SceneObj_Area_Zd_Property_Ani_Prop_MeltableIce_02",
                    "SceneObj_Area_Zd_Property_Ani_Prop_MeltableIce_03");

    /** How long shattered ice stays open water before it freezes back. */
    static final int REFREEZE_SECONDS = 10;

    private MeltableIceHelper() {}

    public static boolean refreezes(EntityGadget gadget) {
        var data = gadget.getGadgetData();
        return data != null && REFREEZING_ICE.contains(data.getJsonName());
    }

    /** Schedules shattered ice to come back as it was placed by its group. */
    public static void onShattered(EntityGadget gadget) {
        if (!refreezes(gadget)) return;
        var meta = gadget.getMetaGadget();
        var scene = gadget.getScene();
        if (meta == null || scene == null || gadget.getGroupId() == 0) return;

        int groupId = gadget.getGroupId();
        int blockId = gadget.getBlockId();
        int tickRateMs = Math.max(1, Configuration.GAME_INFO.tickRateMs);
        int ticks = (int) Math.ceil(REFREEZE_SECONDS * 1000d / tickRateMs);

        scene.getScheduler()
                .scheduleDelayedTask(
                        () -> {
                            var scriptManager = scene.getScriptManager();
                            var group = scriptManager.getGroupInstanceById(groupId);
                            if (group == null) return; // the group unloaded; it respawns the ice itself
                            boolean present =
                                    scene.getEntities().values().stream()
                                            .anyMatch(
                                                    e ->
                                                            e.getGroupId() == groupId
                                                                    && e.getConfigId() == meta.config_id);
                            if (present) return;

                            group.getDeadEntities().remove(meta.config_id);
                            var ice = scriptManager.createGadget(groupId, blockId, meta);
                            if (ice == null) return;
                            scriptManager.addEntity(ice);
                            Grasscutter.getLogger()
                                    .debug(
                                            "[MeltableIce] refroze group={} config={} as entity {}",
                                            groupId,
                                            meta.config_id,
                                            ice.getId());
                        },
                        ticks);
    }
}
