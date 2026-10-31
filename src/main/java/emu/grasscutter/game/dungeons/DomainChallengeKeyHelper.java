/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.entity.EntityGadget
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.entity.gadget.GadgetContent
 *  emu.grasscutter.game.entity.gadget.GadgetWorktop
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.scripts.SceneScriptManager
 *  emu.grasscutter.scripts.data.SceneGadget
 *  emu.grasscutter.scripts.data.SceneGroup
 *  emu.grasscutter.server.packet.send.PacketWorktopOptionNotify
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.GadgetContent;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.data.SceneGadget;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.packet.send.PacketWorktopOptionNotify;
import java.util.concurrent.ConcurrentHashMap;

public final class DomainChallengeKeyHelper {
    private static final int CHALLENGE_KEY_GADGET_ID = 70350096;
    private static final int CHALLENGE_WORKTOP_GADGET_ID = 70360010;
    private static final int LEYLINE_CHALLENGE_KEY_GADGET_ID = 73051002;
    private static final int MAMOLU_KEY_ENTITY_ID = 70350035;
    private static final int START_OPTION_ID = 7;
    private static final int HIDE_DELAY_SECONDS = 2;
    private static final int WORKTOP_HIDE_DELAY_SECONDS = 1;
    private static final ConcurrentHashMap<Long, Integer> PENDING_HIDE = new ConcurrentHashMap();

    private DomainChallengeKeyHelper() {
    }

    public static void ensureKeyWorktop(EntityGadget entityGadget) {
        if (entityGadget == null || entityGadget.getGadgetId() != 70350035) {
            return;
        }
        if (entityGadget.getContent() instanceof GadgetWorktop) {
            return;
        }
        entityGadget.setContent((GadgetContent)new GadgetWorktop(entityGadget));
    }

    public static boolean isChallengeKeyGroup(SceneScriptManager sceneScriptManager, int n) {
        if (sceneScriptManager == null) {
            return false;
        }
        SceneGroup sceneGroup = sceneScriptManager.getGroupById(n);
        if (sceneGroup == null || sceneGroup.gadgets == null) {
            return false;
        }
        for (SceneGadget sceneGadget : sceneGroup.gadgets.values()) {
            if (sceneGadget == null || !DomainChallengeKeyHelper.isChallengeKeyGadgetId(sceneGadget.gadget_id)) continue;
            return true;
        }
        return false;
    }

    public static boolean isChallengeKeyGadgetId(int n) {
        return n == 70350096 || n == 70360010 || n == 73051002 || n == 70350035;
    }

    public static void onWorktopStartSelected(Scene scene, EntityGadget entityGadget, int n) {
        if (scene == null || entityGadget == null || n != 7 || !DomainDungeonHelper.isDomainScene(scene) || !DomainChallengeKeyHelper.isChallengeKeyGadgetId(entityGadget.getGadgetId())) {
            return;
        }
        if (entityGadget.getGadgetId() == 70350035) {
            return;
        }
        long l = (long)scene.getId() << 32 | (long)entityGadget.getId() & 0xFFFFFFFFL;
        if (PENDING_HIDE.putIfAbsent(l, entityGadget.getId()) != null) {
            return;
        }
        if (Grasscutter.getGameServer() == null) {
            PENDING_HIDE.remove(l);
            return;
        }
        Grasscutter.getGameServer().getScheduler().scheduleDelayedTask(() -> {
            PENDING_HIDE.remove(l);
            DomainChallengeKeyHelper.hideWorktopKey(scene, entityGadget.getGroupId(), entityGadget.getConfigId());
        }, 1);
    }

    public static void hideWorktopKey(Scene scene, int n, int n2) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        GameEntity gameEntity = scene.getEntityByConfigId(n2, n);
        if (!(gameEntity instanceof EntityGadget)) {
            return;
        }
        EntityGadget entityGadget = (EntityGadget)gameEntity;
        if (!DomainChallengeKeyHelper.isChallengeKeyGadgetId(entityGadget.getGadgetId())) {
            return;
        }
        if (entityGadget.getGadgetId() == 70350035) {
            return;
        }
        GadgetContent gadgetContent = entityGadget.getContent();
        if (gadgetContent instanceof GadgetWorktop) {
            GadgetWorktop gadgetWorktop = (GadgetWorktop)gadgetContent;
            gadgetWorktop.removeWorktopOption(7);
            scene.broadcastPacket((BasePacket)new PacketWorktopOptionNotify(entityGadget));
        }
        if (entityGadget.getState() != 202) {
            entityGadget.updateState(202);
        }
        Grasscutter.getLogger().debug("Hid domain challenge worktop group={} config={} scene={}", new Object[]{n, n2, scene.getId()});
    }

    public static void revealChallengeKeys(Scene scene) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        int n = 0;
        for (GameEntity gameEntity : scene.getEntities().values()) {
            EntityGadget entityGadget;
            if (!(gameEntity instanceof EntityGadget) || !DomainChallengeKeyHelper.isChallengeKeyGadgetId((entityGadget = (EntityGadget)gameEntity).getGadgetId())) continue;
            DomainChallengeKeyHelper.ensureKeyWorktop(entityGadget);
            GadgetContent gadgetContent = entityGadget.getContent();
            if (!(gadgetContent instanceof GadgetWorktop)) {
                if (entityGadget.getState() == 0) continue;
                entityGadget.updateState(0);
                ++n;
                continue;
            }
            GadgetWorktop gadgetWorktop = (GadgetWorktop)gadgetContent;
            if (entityGadget.getState() != 0) {
                entityGadget.updateState(0);
            }
            if (!gadgetWorktop.getWorktopOptions().contains(7)) {
                gadgetWorktop.addWorktopOptions(new int[]{7});
            }
            scene.broadcastPacket((BasePacket)new PacketWorktopOptionNotify(entityGadget));
            ++n;
        }
        if (n > 0) {
            Grasscutter.getLogger().info("Revealed {} domain challenge key(s) scene={}", (Object)n, (Object)scene.getId());
        }
    }

    public static void scheduleHideKey(Scene scene, int n) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        long l = (long)scene.getId() << 32 | (long)n & 0xFFFFFFFFL;
        PENDING_HIDE.put(l, n);
        if (Grasscutter.getGameServer() == null) {
            return;
        }
        Grasscutter.getGameServer().getScheduler().scheduleDelayedTask(() -> {
            PENDING_HIDE.remove(l);
            SceneScriptManager sceneScriptManager = scene.getScriptManager();
            if (sceneScriptManager == null || !sceneScriptManager.isInit()) {
                return;
            }
            if (sceneScriptManager.refreshGroupSuite(n, 1)) {
                Grasscutter.getLogger().debug("Hid domain challenge key group {} in scene {}", (Object)n, (Object)scene.getId());
            }
        }, 2);
    }
}

