/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.packet.Opcodes
 *  emu.grasscutter.net.packet.PacketHandler
 *  emu.grasscutter.net.proto.GetBattlePassProductReq
 *  emu.grasscutter.server.game.GameSession
 *  emu.grasscutter.server.packet.send.PacketGetBattlePassProductRsp
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.GetBattlePassProductReqOuterClass.GetBattlePassProductReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetBattlePassProductRsp;

@Opcodes(PacketOpcodes.GetBattlePassProductReq)
public class HandlerGetBattlePassProductReq
extends PacketHandler {
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) throws Exception {
        int n = 0;
        try {
            GetBattlePassProductReq req = GetBattlePassProductReq.parseFrom((byte[])byArray2);
            n = req.getBattlePassProductPlayType();
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("GetBattlePassProductReq parse failed", throwable);
        }
        String productId = "10201";
        String string = "Tier_ugcbp_5";
        if (n == 2) {
            productId = "10202";
            string = "Tier_ugcbp_15";
        } else if (n == 3) {
            productId = "10203";
            string = "Tier_ugcbp_10";
        }
        Grasscutter.getLogger().debug("GetBattlePassProductReq uid={} playType={} product={}", new Object[]{gameSession.getPlayer() != null ? gameSession.getPlayer().getUid() : 0, n, productId});
        gameSession.send((BasePacket)new PacketGetBattlePassProductRsp(productId, string, n, 6700));
    }
}

