/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.ELBLIGDECNMReq)
public class HandlerELBLIGDECNMReq
extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) {
        gameSession.send(new BasePacket(PacketOpcodes.ELBLIGDECNMRsp2));
        gameSession.send(new BasePacket(PacketOpcodes.ELBLIGDECNMRsp));
        Grasscutter.getLogger().debug("Handled ELBLIGDECNMReq ({}) for uid {}", PacketOpcodes.ELBLIGDECNMReq, gameSession.getPlayer().getUid());
    }
}
