package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.icecreature.IceCreatureSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.IceCreatureBuildReqOuterClass.IceCreatureBuildReq;
import emu.grasscutter.server.game.GameSession;

/** Snezhnaya Icemaking: build a creature where the client placed it. */
@Opcodes(PacketOpcodes.IceCreatureBuildReq)
public class HandlerIceCreatureBuildReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        IceCreatureSystem.onBuild(session.getPlayer(), IceCreatureBuildReq.parseFrom(payload));
    }
}
