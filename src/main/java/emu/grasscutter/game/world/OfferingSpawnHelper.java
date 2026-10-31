package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.GadgetOffering;
import emu.grasscutter.game.entity.gadget.OfferingHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.EntityType;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sacred Sakura interaction fallback:
 *
 * <ul>
 *   <li>Never delete the script group entity (133220200 / config 200001)
 *   <li>Only tear down our own synthesised duplicate when a real scripted sakura exists
 *   <li>Spawn one only when none exists at all
 * </ul>
 */
public final class OfferingSpawnHelper {
    private static final int SCENE_ID = 3;
    private static final int SCRIPT_GROUP = 133220200;
    private static final int SCRIPT_CONFIG = 200001;
    private static final int SYNTH_GROUP = 910030001;
    private static final int SYNTH_CONFIG = 9100301;
    private static final float NEAR_DIST = 100f;
    private static final float LEAVE_DIST = 180f;
    private static final long CHECK_INTERVAL_MS = 2000L;

    /** Script slot: scene3_group133220200 config 200001. */
    private static final Position SAKURA_POS =
            new Position(-2465.643f, 449.196f, -4422.203f);

    private static final Map<Integer, Long> lastCheckMs = new ConcurrentHashMap<>();
    private static final java.util.Set<Integer> notifiedUids =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Integer> refreshedEntityIds =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean spawnedSakura = false;

    private OfferingSpawnHelper() {}

    public static void ensureNearby(Scene scene) {
        if (scene == null || scene.getId() != SCENE_ID) {
            return;
        }
        if (scene.getPlayers() == null || scene.getPlayers().isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean anyNear = false;
        for (Player player : scene.getPlayers()) {
            if (player == null || player.getPosition() == null) {
                continue;
            }
            if (notifiedUids.add(player.getUid())) {
                try {
                    OfferingHelper.onPlayerLogin(player);
                } catch (Throwable t) {
                    notifiedUids.remove(player.getUid());
                }
            }
            Long last = lastCheckMs.get(player.getUid());
            if (last == null || now - last >= CHECK_INTERVAL_MS) {
                lastCheckMs.put(player.getUid(), now);
            }
            if (player.getPosition().computeDistance(SAKURA_POS) <= NEAR_DIST) {
                anyNear = true;
            }
        }

        if (anyNear) {
            ensureSacredSakura(scene);
        } else {
            maybeDespawnSynthOnly(scene);
        }
    }

    private static void ensureSacredSakura(Scene scene) {
        List<EntityGadget> all = findSakuraGadgets(scene, 50f);
        List<EntityGadget> realOnes = new ArrayList<>();
        List<EntityGadget> synthOnes = new ArrayList<>();
        for (EntityGadget g : all) {
            if (isSynth(g)) {
                synthOnes.add(g);
            } else {
                realOnes.add(g);
            }
        }

        // A real scripted or born entity exists: remove only synthesised duplicates, never the real one.
        if (!realOnes.isEmpty()) {
            for (EntityGadget g : synthOnes) {
                scene.removeEntity(g, VisionType.VisionType_VISION_REMOVE);
                refreshedEntityIds.remove(g.getId());
            }
            spawnedSakura = false;
            for (EntityGadget g : realOnes) {
                ensureInteractable(scene, g);
            }
            Grasscutter.getLogger()
                    .debug(
                            "OfferingSpawnHelper keep real sakura count={} removedSynth={}",
                            realOnes.size(),
                            synthOnes.size());
            return;
        }

        // Only synthesised ones: keep exactly one and make sure it is interactable.
        if (!synthOnes.isEmpty()) {
            EntityGadget keep = synthOnes.get(0);
            for (int i = 1; i < synthOnes.size(); i++) {
                scene.removeEntity(synthOnes.get(i), VisionType.VisionType_VISION_REMOVE);
                refreshedEntityIds.remove(synthOnes.get(i).getId());
            }
            ensureInteractable(scene, keep);
            spawnedSakura = true;
            return;
        }

        // None at all: spawn one, which happens when the script group is not loaded.
        spawnSynth(scene);
    }

    private static void spawnSynth(Scene scene) {
        try {
            EntityGadget gadget =
                    new EntityGadget(
                            scene,
                            OfferingHelper.GADGET_SACRED_SAKURA,
                            SAKURA_POS.clone(),
                            new Position(0f, 332.221f, 0f));
            gadget.setGroupId(SYNTH_GROUP);
            gadget.setConfigId(SYNTH_CONFIG);
            gadget.setInteractEnabled(true);
            gadget.buildContent();
            if (!(gadget.getContent() instanceof GadgetOffering)) {
                gadget.replaceContent(new GadgetOffering(gadget));
            }
            scene.addEntity(gadget);
            spawnedSakura = true;
            refreshedEntityIds.add(gadget.getId());
            Grasscutter.getLogger()
                    .info(
                            "OfferingSpawnHelper spawn SacredSakura synth at {},{},{}",
                            (int) SAKURA_POS.getX(),
                            (int) SAKURA_POS.getY(),
                            (int) SAKURA_POS.getZ());
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("OfferingSpawnHelper spawn failed: {}", t.toString());
        }
    }

    private static boolean isSynth(EntityGadget g) {
        return g.getGroupId() == SYNTH_GROUP || g.getConfigId() == SYNTH_CONFIG;
    }

    private static boolean isScriptSakura(EntityGadget g) {
        return g.getGroupId() == SCRIPT_GROUP || g.getConfigId() == SCRIPT_CONFIG;
    }

    private static List<EntityGadget> findSakuraGadgets(Scene scene, float radius) {
        List<EntityGadget> out = new ArrayList<>();
        for (GameEntity ge : scene.getEntities().values()) {
            if (!(ge instanceof EntityGadget g)) {
                continue;
            }
            if (g.getGadgetId() != OfferingHelper.GADGET_SACRED_SAKURA) {
                continue;
            }
            if (g.getPosition() != null && g.getPosition().computeDistance(SAKURA_POS) <= radius) {
                out.add(g);
            }
        }
        return out;
    }

    /** Attaches GadgetOffering, forces interactability and re-sends Appear to the client when needed. */
    private static void ensureInteractable(Scene scene, EntityGadget g) {
        try {
            boolean needRefresh = false;
            if (!(g.getContent() instanceof GadgetOffering)) {
                g.replaceContent(new GadgetOffering(g));
                needRefresh = true;
            }
            if (!g.isInteractEnabled()) {
                g.setInteractEnabled(true);
                needRefresh = true;
            } else {
                g.setInteractEnabled(true);
            }
            // Force-refresh each entity only once to avoid spam, while still getting offering_info to the client.
            if (needRefresh || refreshedEntityIds.add(g.getId())) {
                scene.broadcastPacket(new PacketSceneEntityAppearNotify(g));
                Grasscutter.getLogger()
                        .info(
                                "OfferingSpawnHelper refresh sakura entityId={} group={} cfg={} script={}",
                                g.getId(),
                                g.getGroupId(),
                                g.getConfigId(),
                                isScriptSakura(g));
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().debug("OfferingSpawnHelper ensureInteractable: {}", t.toString());
        }
    }

    /** Reclaims synthesised entities only; a real scripted entity is never removed for being far away. */
    private static void maybeDespawnSynthOnly(Scene scene) {
        if (!spawnedSakura) {
            return;
        }
        for (Player player : scene.getPlayers()) {
            if (player != null
                    && player.getPosition() != null
                    && player.getPosition().computeDistance(SAKURA_POS) <= LEAVE_DIST) {
                return;
            }
        }
        for (GameEntity ge : new ArrayList<>(scene.getEntities().values())) {
            if (!(ge instanceof EntityGadget g) || !isSynth(g)) {
                continue;
            }
            refreshedEntityIds.remove(g.getId());
            scene.removeEntity(g, VisionType.VisionType_VISION_REMOVE);
        }
        spawnedSakura = false;
    }

    public static void onGadgetCreated(EntityGadget gadget) {
        if (gadget == null) {
            return;
        }
        try {
            boolean offeringType =
                    gadget.getGadgetData() != null
                            && gadget.getGadgetData().getType() == EntityType.OfferingGadget;
            if (!offeringType && OfferingHelper.resolveOfferingId(gadget) <= 0) {
                return;
            }
            if (gadget.getContent() instanceof GadgetOffering) {
                gadget.setInteractEnabled(true);
                return;
            }
            gadget.replaceContent(new GadgetOffering(gadget));
            gadget.setInteractEnabled(true);
        } catch (Throwable ignored) {
        }
    }
}
