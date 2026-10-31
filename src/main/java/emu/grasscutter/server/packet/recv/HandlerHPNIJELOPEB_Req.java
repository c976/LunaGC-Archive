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

@Opcodes(PacketOpcodes.HPNIJELOPEB_Req)
public class HandlerHPNIJELOPEB_Req
extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) {
        gameSession.send(new BasePacket(PacketOpcodes.HPNIJELOPEB_Rsp));
        Grasscutter.getLogger().debug("Handled HPNIJELOPEB ({}) for uid {}", PacketOpcodes.HPNIJELOPEB_Req, gameSession.getPlayer().getUid());
    }
}
