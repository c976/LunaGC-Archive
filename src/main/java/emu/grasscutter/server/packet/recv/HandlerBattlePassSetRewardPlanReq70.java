/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.protobuf.CodedOutputStream
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.packet.Opcodes
 *  emu.grasscutter.net.packet.PacketHandler
 *  emu.grasscutter.net.proto._BattlePassSetRewardPlanReq
 *  emu.grasscutter.server.game.GameSession
 *  emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.battlepass.BattlePassCompatHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BattlePassSetRewardPlanReq._BattlePassSetRewardPlanReq;
import emu.grasscutter.net.proto.BattlePassSetRewardPlanRsp._BattlePassSetRewardPlanRsp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBattlePassCurScheduleUpdateNotify;
import emu.grasscutter.server.packet.send.PacketBeyondBattlePassCurScheduleUpdateNotify;

// 7.0 sent this as CmdId 4415 and answered on 3557, both pinned here with 7.0 field numbers;
// 7.1 moved the pair to _BattlePassSetRewardPlanReq/Rsp, read and written through their classes.
@Opcodes(PacketOpcodes._BattlePassSetRewardPlanReq)
public class HandlerBattlePassSetRewardPlanReq70 extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] header, byte[] payload) throws Exception {
        Player player = gameSession.getPlayer();
        int plan = 1;
        boolean noRemind = false;
        try {
            var req = _BattlePassSetRewardPlanReq.parseFrom(payload);
            if (req.getBattlePassPlan() > 0) plan = req.getBattlePassPlan();
            noRemind = req.getIsNoRemind();
        } catch (Throwable throwable) {
            Grasscutter.getLogger().warn("SetRewardPlan parse failed", throwable);
        }
        if (player != null) {
            BattlePassCompatHelper.setSelectedPlan(player, plan);
            Grasscutter.getLogger().debug("SetRewardPlan uid={} plan={} noRemind={}", player.getUid(), plan, noRemind);
        }
        var rsp = _BattlePassSetRewardPlanRsp.newBuilder().setBattlePassPlan(plan);
        for (int tier = 1; tier <= 5; ++tier) rsp.addAffectedTierIdList(tier);
        var packet = new BasePacket(PacketOpcodes._BattlePassSetRewardPlanRsp);
        packet.setData(rsp.build());
        gameSession.send(packet);
        if (player != null) {
            player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
            player.sendPacket(new PacketBeyondBattlePassCurScheduleUpdateNotify(player));
        }
    }
}
