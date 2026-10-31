package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.expedition.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionGetRewardReqOuterClass.AvatarExpeditionGetRewardReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.AvatarExpeditionRewardInfo._AvatarExpeditionRewardInfo;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;
import java.util.*;

@Opcodes(PacketOpcodes.AvatarExpeditionGetRewardReq)
public class HandlerAvatarExpeditionGetRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        AvatarExpeditionGetRewardReq req = AvatarExpeditionGetRewardReq.parseFrom(payload);
        var player = session.getPlayer();

        int clientSeq = 0;
        try {
            if (header != null && header.length > 0) {
                clientSeq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Exception ignored) {
        }

        ExpeditionHelper.refreshFinishedStates(player);

        List<GameItem> allItems = new ArrayList<>();
        List<_AvatarExpeditionRewardInfo> rewardInfos = new ArrayList<>();

        List<Map.Entry<Long, ExpeditionInfo>> toClaim = new ArrayList<>();
        if (req.getIsClaimAll()) {
            for (var entry : player.getExpeditionInfo().entrySet()) {
                if (entry.getValue().getState() == 2) {
                    toClaim.add(entry);
                }
            }
        } else {
            ExpeditionInfo expInfo = player.getExpeditionInfo(req.getAvatarGuid());
            if (expInfo != null && expInfo.getState() == 2) {
                toClaim.add(Map.entry(req.getAvatarGuid(), expInfo));
            }
        }

        for (var entry : toClaim) {
            long guid = entry.getKey();
            ExpeditionInfo expInfo = entry.getValue();
            List<GameItem> items = ExpeditionHelper.rollRewards(player, expInfo);
            allItems.addAll(items);
            rewardInfos.add(PacketAvatarExpeditionGetRewardRsp.toRewardInfo(guid, expInfo, items));
            player.removeExpeditionInfo(guid);
        }

        if (!allItems.isEmpty()) {
            player.getInventory().addItems(allItems, ActionReason.ExpeditionReward);
        }

        player.save();
        var rsp = new PacketAvatarExpeditionGetRewardRsp(player.getExpeditionInfo(), rewardInfos);
        if (clientSeq > 0) {
            rsp.buildHeader(clientSeq);
        }
        session.send(rsp);
        session.send(new PacketAvatarExpeditionDataNotify(player.getExpeditionInfo()));
        session.send(
                new PacketAvatarExpeditionAllDataRsp(
                        player.getExpeditionInfo(), player.getExpeditionLimit()));
    }
}
