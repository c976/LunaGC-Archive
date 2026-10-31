/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.FNBOONCENJP_Req)
public class HandlerFNBOONCENJP_Req
extends PacketHandler {
    @Override
    public void handle(GameSession gameSession, byte[] byArray, byte[] byArray2) {
    }
}
