package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.icecreature.IceCreatureSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.IceCreatureLikeReqOuterClass.IceCreatureLikeReq;
import emu.grasscutter.server.game.GameSession;

/** Snezhnaya Icemaking: like a creature someone built. */
@Opcodes(PacketOpcodes.IceCreatureLikeReq)
public class HandlerIceCreatureLikeReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        IceCreatureSystem.onLike(session.getPlayer(), IceCreatureLikeReq.parseFrom(payload));
    }
}
