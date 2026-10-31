package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.icecreature.IceCreatureSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.IceCreatureSetTypeGadgetReqOuterClass.IceCreatureSetTypeGadgetReq;
import emu.grasscutter.server.game.GameSession;

/** Snezhnaya Icemaking: the build wheel switched one creature type to another look. */
@Opcodes(PacketOpcodes.IceCreatureSetTypeGadgetReq)
public class HandlerIceCreatureSetTypeGadgetReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        IceCreatureSystem.onSetTypeGadget(
                session.getPlayer(), IceCreatureSetTypeGadgetReq.parseFrom(payload));
    }
}
