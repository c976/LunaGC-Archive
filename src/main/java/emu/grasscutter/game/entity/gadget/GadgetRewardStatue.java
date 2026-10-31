package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DomainRewardStatueHelper;
import emu.grasscutter.game.dungeons.DomainStatueClaimHelper;
import emu.grasscutter.game.dungeons.DomainStatueClaimHelper.ClaimMode;
import emu.grasscutter.game.dungeons.DomainStatueDropService;
import emu.grasscutter.game.dungeons.DungeonDropLoader;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.server.packet.send.PacketGadgetInteractRsp;

public final class GadgetRewardStatue extends GadgetContent {

    public GadgetRewardStatue(EntityGadget gadget) {
        super(gadget);
    }

    @Override
    public boolean onInteract(Player player, GadgetInteractReq req) {
        DungeonManager dm = player.getScene() == null ? null : player.getScene().getDungeonManager();
        if (dm == null) {
            Grasscutter.getLogger().warn("GadgetRewardStatue claim uid={} no DungeonManager", player.getUid());
            return false;
        }

        if (!dm.isFinishedSuccessfully()) {
            try {
                dm.triggerEvent(DungeonPassConditionType.DUNGEON_COND_FINISH_CHALLENGE, new int[] {1, 0, 0});
            } catch (Exception ignored) {
            }
            try {
                if (dm.isFinishedSuccessfully()) {
                    dm.finishDungeon();
                }
            } catch (Exception ignored) {
            }
        }

        // Step 1: open the petrified tree selection UI without claiming yet.
        if (DomainStatueClaimHelper.shouldOpenUiOnly(req)) {
            player.sendPacket(
                    new PacketGadgetInteractRsp(
                            getGadget(),
                            InteractType.InteractType_INTERACT_OPEN_STATUE,
                            InterOpType.InterOpType_INTER_OP_START));
            Grasscutter.getLogger()
                    .info(
                            "GadgetRewardStatue open UI uid={} finished={} resin={}",
                            player.getUid(),
                            dm.isFinishedSuccessfully(),
                            DomainRewardStatueHelper.getCurrentResin(player));
            return false;
        }

        ClaimMode mode;
        try {
            mode = DomainStatueClaimHelper.resolve(req);
            DomainStatueClaimHelper.logInteract(player.getUid(), req, mode);
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .error("GadgetRewardStatue resolve failed uid={}", player.getUid(), t);
            return false;
        }

        if (!dm.isFinishedSuccessfully()) {
            player.sendPacket(
                    new PacketGadgetInteractRsp(getGadget(), InteractType.InteractType_INTERACT_OPEN_STATUE));
            DomainRewardStatueHelper.remindAfterFailedClaim(player, dm, getGadget());
            return false;
        }

        // Original-resin rows: insufficient shows the replenish popup and keeps the tree claimable.
        if (mode.originalResinCost > 0
                && DomainRewardStatueHelper.getCurrentResin(player) < mode.originalResinCost) {
            DomainRewardStatueHelper.sendResinNotEnoughPopup(player, getGadget());
            return false;
        }

        player.sendPacket(
                new PacketGadgetInteractRsp(getGadget(), InteractType.InteractType_INTERACT_OPEN_STATUE));
        try {
            DungeonDropLoader.ensureLoaded();
        } catch (Throwable ignored) {
        }

        boolean ok;
        try {
            ok = DomainStatueDropService.claim(player, dm, mode, getGadget().getGroupId());
        } catch (Throwable t) {
            Grasscutter.getLogger()
                    .error(
                            "GadgetRewardStatue claim threw uid={} mode={}",
                            player.getUid(),
                            mode,
                            t);
            DomainRewardStatueHelper.remindAfterFailedClaim(player, dm, getGadget());
            return false;
        }
        Grasscutter.getLogger()
                .info(
                        "GadgetRewardStatue claim result uid={} mode={} ok={}",
                        player.getUid(),
                        mode,
                        ok);
        if (!ok) {
            DomainRewardStatueHelper.remindAfterFailedClaim(player, dm, getGadget());
        }
        // Always false: EntityGadget kills the gadget when onInteract returns true.
        return false;
    }

    @Override
    public void onBuildProto(SceneGadgetInfo.Builder gadgetInfo) {}
}
