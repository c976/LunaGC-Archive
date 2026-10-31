/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.data.excels.dungeon.DungeonData
 *  emu.grasscutter.game.dungeons.DungeonDropLoader
 *  emu.grasscutter.game.dungeons.DungeonManager
 *  emu.grasscutter.game.dungeons.enums.DungeonPassConditionType
 *  emu.grasscutter.game.entity.EntityBaseGadget
 *  emu.grasscutter.game.entity.EntityGadget
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.entity.gadget.GadgetContent
 *  emu.grasscutter.game.entity.gadget.GadgetRewardStatue
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.PlayerProperty
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.GadgetInteractReqOuterClass$GadgetInteractReq
 *  emu.grasscutter.net.proto.InteractTypeOuterClass$InteractType
 *  emu.grasscutter.net.proto.ResinCostTypeOuterClass$ResinCostType
 *  emu.grasscutter.net.proto.VisionTypeOuterClass$VisionType
 *  emu.grasscutter.server.packet.send.PacketGadgetInteractResinNotEnoughRsp
 *  emu.grasscutter.server.packet.send.PacketGadgetInteractRsp
 *  emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify
 *  emu.grasscutter.server.packet.send.PacketSceneEntityDisappearNotify
 */
package emu.grasscutter.game.dungeons;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.excels.dungeon.DungeonData;
import emu.grasscutter.game.dungeons.DomainChallengeKeyHelper;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.dungeons.DungeonDropLoader;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.gadget.GadgetContent;
import emu.grasscutter.game.entity.gadget.GadgetRewardStatue;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass;
import emu.grasscutter.net.proto.InteractTypeOuterClass;
import emu.grasscutter.net.proto.ResinCostTypeOuterClass;
import emu.grasscutter.net.proto.VisionTypeOuterClass;
import emu.grasscutter.server.packet.send.PacketGadgetInteractResinNotEnoughRsp;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;
import emu.grasscutter.server.packet.send.PacketSceneEntityAppearNotify;
import emu.grasscutter.server.packet.send.PacketSceneEntityDisappearNotify;
import java.lang.reflect.Field;
import java.util.ArrayList;

public final class DomainRewardStatueHelper {
    public static final int MAMOLU_GADGET_ID = 70350023;
    public static final int REWARD_TREE_GADGET_ID = 70350008;
    public static final int REWARD_WORKTOP_CONFIG_ID = 5001;
    public static final int CHALLENGE_KEY_CONFIG_ID = 3012;

    private DomainRewardStatueHelper() {
    }

    public static boolean isRewardMamolu(EntityGadget entityGadget) {
        return entityGadget != null && entityGadget.getGadgetId() == 70350023;
    }

    public static boolean isExitRewardStatue(int n) {
        return n == 70340011 || n == 70340012 || n == 70340013 || n == 70340014 || n == 70380008 || n == 70350008;
    }

    public static boolean isClaimableState(int n) {
        return n == 203 || n == 401;
    }

    public static void onChallengeSuccess(Scene scene) {
        if (scene == null || !DomainDungeonHelper.isDomainScene(scene)) {
            return;
        }
        DungeonManager dungeonManager = scene.getDungeonManager();
        boolean bl = false;
        if (dungeonManager != null) {
            try {
                dungeonManager.triggerEvent(DungeonPassConditionType.DUNGEON_COND_FINISH_CHALLENGE, new int[]{1, 0, 0});
            }
            catch (Exception exception) {
                Grasscutter.getLogger().warn("trigger FINISH_CHALLENGE failed scene={}", (Object)scene.getId(), (Object)exception);
            }
            bl = dungeonManager.isFinishedSuccessfully();
            if (bl) {
                try {
                    dungeonManager.finishDungeon();
                }
                catch (Exception exception) {
                    Grasscutter.getLogger().warn("finishDungeon failed scene={}", (Object)scene.getId(), (Object)exception);
                }
            }
        }
        int n = 0;
        int n2 = 0;
        ArrayList<EntityGadget> arrayList = new ArrayList<EntityGadget>();
        ArrayList<EntityGadget> arrayList2 = new ArrayList<EntityGadget>();
        for (GameEntity gameEntity : scene.getEntities().values()) {
            if (!(gameEntity instanceof EntityGadget)) continue;
            EntityGadget entityGadget = (EntityGadget)gameEntity;
            int n3 = entityGadget.getGadgetId();
            int n4 = entityGadget.getConfigId();
            if (n4 == 3012 && DomainChallengeKeyHelper.isChallengeKeyGadgetId(n3)) {
                arrayList.add(entityGadget);
                continue;
            }
            if (!DomainRewardStatueHelper.isExitRewardStatue(n3)) continue;
            DomainRewardStatueHelper.forceRewardStatueContent(entityGadget);
            entityGadget.setInteractEnabled(true);
            entityGadget.updateState(401);
            ++n2;
            arrayList2.add(entityGadget);
        }
        for (EntityGadget entityGadget : arrayList) {
            scene.killEntity((GameEntity)entityGadget, 0);
            ++n;
        }
        for (EntityGadget entityGadget : arrayList2) {
            try {
                scene.broadcastPacket((BasePacket)new PacketSceneEntityDisappearNotify((GameEntity)entityGadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));
                scene.broadcastPacket((BasePacket)new PacketSceneEntityAppearNotify((GameEntity)entityGadget));
            }
            catch (Exception exception) {
                try {
                    scene.broadcastPacket((BasePacket)new PacketSceneEntityAppearNotify((GameEntity)entityGadget));
                }
                catch (Exception exception2) {}
            }
        }
        Grasscutter.getLogger().info("Domain reward tree scene={} keysKilled={} exitLit={} dmFinished={}", new Object[]{scene.getId(), n, n2, bl});
    }

    private static void forceRewardStatueContent(EntityGadget entityGadget) {
        if (entityGadget.getContent() instanceof GadgetRewardStatue) {
            return;
        }
        try {
            Field field = EntityGadget.class.getDeclaredField("content");
            field.setAccessible(true);
            field.set(entityGadget, new GadgetRewardStatue(entityGadget));
        }
        catch (Exception exception) {
            entityGadget.setContent((GadgetContent)new GadgetRewardStatue(entityGadget));
        }
    }

    public static int getClaimResinCost(DungeonManager dungeonManager) {
        if (dungeonManager == null) {
            return 0;
        }
        DungeonData dungeonData = dungeonManager.getDungeonData();
        if (dungeonData == null) {
            return 0;
        }
        int n = dungeonData.getStatueCostID();
        if (n != 0 && n != 106) {
            return 0;
        }
        int n2 = dungeonData.getStatueCostCount();
        if (n2 < 0) {
            return 0;
        }
        if (n == 0) {
            return 0;
        }
        return n2 > 0 ? n2 : 20;
    }

    public static int getCurrentResin(Player player) {
        if (player == null) {
            return 0;
        }
        try {
            return player.getProperty(PlayerProperty.PROP_PLAYER_RESIN);
        }
        catch (Exception exception) {
            return 0;
        }
    }

    public static boolean isResinInsufficient(Player player, DungeonManager dungeonManager) {
        int n = DomainRewardStatueHelper.getClaimResinCost(dungeonManager);
        if (n <= 0) {
            return false;
        }
        return DomainRewardStatueHelper.getCurrentResin(player) < n;
    }

    public static void sendResinNotEnoughPopup(Player player, EntityGadget entityGadget) {
        if (player == null) {
            return;
        }
        try {
            player.sendPacket((BasePacket)new PacketGadgetInteractResinNotEnoughRsp((EntityBaseGadget)entityGadget));
            DomainRewardStatueHelper.keepRewardTreeClaimable(player, entityGadget);
            Grasscutter.getLogger().info("Domain resin popup uid={} ret={} have={} need={}", new Object[]{player.getUid(), 660, DomainRewardStatueHelper.getCurrentResin(player), player.getScene() != null && player.getScene().getDungeonManager() != null ? DomainRewardStatueHelper.getClaimResinCost(player.getScene().getDungeonManager()) : -1});
        }
        catch (Exception exception) {
            Grasscutter.getLogger().warn("sendResinNotEnoughPopup failed uid={}", (Object)player.getUid(), (Object)exception);
        }
    }

    public static void keepRewardTreeClaimable(Player player, EntityGadget entityGadget) {
        if (entityGadget == null) {
            return;
        }
        try {
            Scene scene;
            entityGadget.setInteractEnabled(true);
            if (!DomainRewardStatueHelper.isClaimableState(entityGadget.getState())) {
                entityGadget.updateState(401);
            } else {
                entityGadget.updateState(0);
                entityGadget.updateState(401);
            }
            Scene scene2 = scene = player != null ? player.getScene() : entityGadget.getScene();
            if (scene != null) {
                try {
                    scene.broadcastPacket((BasePacket)new PacketSceneEntityDisappearNotify((GameEntity)entityGadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE));
                    scene.broadcastPacket((BasePacket)new PacketSceneEntityAppearNotify((GameEntity)entityGadget));
                }
                catch (Exception exception) {
                    scene.broadcastPacket((BasePacket)new PacketSceneEntityAppearNotify((GameEntity)entityGadget));
                }
            }
        }
        catch (Exception exception) {
            Grasscutter.getLogger().warn("keepRewardTreeClaimable failed", (Throwable)exception);
        }
    }

    public static void remindAfterFailedClaim(Player player, DungeonManager dungeonManager, EntityGadget entityGadget) {
        if (player == null) {
            return;
        }
        if (DomainRewardStatueHelper.isResinInsufficient(player, dungeonManager)) {
            DomainRewardStatueHelper.sendResinNotEnoughPopup(player, entityGadget);
        } else if (entityGadget != null) {
            DomainRewardStatueHelper.keepRewardTreeClaimable(player, entityGadget);
        }
    }

    public static void remindAfterFailedClaim(Player player, DungeonManager dungeonManager) {
        DomainRewardStatueHelper.remindAfterFailedClaim(player, dungeonManager, null);
    }

    public static boolean tryClaimFromWorktop(Player player, EntityGadget entityGadget, GadgetInteractReqOuterClass.GadgetInteractReq gadgetInteractReq) {
        boolean bl;
        DungeonManager dungeonManager;
        if (player == null || entityGadget == null || !DomainRewardStatueHelper.isRewardMamolu(entityGadget)) {
            return false;
        }
        if (!DomainRewardStatueHelper.isClaimableState(entityGadget.getState())) {
            return false;
        }
        DungeonManager dungeonManager2 = dungeonManager = player.getScene() != null ? player.getScene().getDungeonManager() : null;
        if (dungeonManager == null) {
            return false;
        }
        if (!dungeonManager.isFinishedSuccessfully()) {
            try {
                dungeonManager.triggerEvent(DungeonPassConditionType.DUNGEON_COND_FINISH_CHALLENGE, new int[]{1, 0, 0});
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (!dungeonManager.isFinishedSuccessfully()) {
            return false;
        }
        boolean bl2 = bl = gadgetInteractReq != null && gadgetInteractReq.getResinCostType() == ResinCostTypeOuterClass.ResinCostType.ResinCostType_CONDENSE;
        if (!bl && DomainRewardStatueHelper.isResinInsufficient(player, dungeonManager)) {
            DomainRewardStatueHelper.sendResinNotEnoughPopup(player, entityGadget);
            return false;
        }
        player.sendPacket((BasePacket)new PacketGadgetInteractRsp((EntityBaseGadget)entityGadget, InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_STATUE));
        try {
            DungeonDropLoader.ensureLoaded();
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        boolean bl3 = dungeonManager.getStatueDrops(player, bl, entityGadget.getGroupId());
        if (!bl3) {
            DomainRewardStatueHelper.remindAfterFailedClaim(player, dungeonManager, entityGadget);
        }
        return bl3;
    }
}

