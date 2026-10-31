/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.packet.Opcodes
 *  emu.grasscutter.net.packet.PacketHandler
 *  emu.grasscutter.net.proto.GachaOpenWishNotifyOuterClass$GachaOpenWishNotify
 *  emu.grasscutter.server.game.GameSession
 *  emu.grasscutter.server.packet.send.PacketGachaSimpleInfoNotify
 */
package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGachaSimpleInfoNotify;

@Opcodes(PacketOpcodes.GachaOpenWishNotify)
public class HandlerGachaOpenWishNotify
extends PacketHandler {
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.send((BasePacket)new PacketGachaSimpleInfoNotify(false));
    }
}

