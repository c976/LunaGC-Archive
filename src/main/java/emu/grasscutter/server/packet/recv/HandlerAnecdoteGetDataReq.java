/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;

// 20526 was the 7.0 CmdId; in 7.1 it belongs to another packet and the request's own is unknown.
@Opcodes(PacketOpcodes._AnecdoteGetDataReq)
public class HandlerAnecdoteGetDataReq
extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) {
        gameSession.send(new BasePacket(PacketOpcodes._AnecdoteGetDataRsp));
        Grasscutter.getLogger().debug("Handled AnecdoteGetDataReq for uid {}", (Object)gameSession.getPlayer().getUid());
    }
}
