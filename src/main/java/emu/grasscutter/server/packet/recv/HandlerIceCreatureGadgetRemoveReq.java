package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.icecreature.IceCreatureSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.IceCreatureGadgetRemoveReqOuterClass.IceCreatureGadgetRemoveReq;
import emu.grasscutter.server.game.GameSession;

/** Snezhnaya Icemaking: tear down the player's own creatures. */
@Opcodes(PacketOpcodes.IceCreatureGadgetRemoveReq)
public class HandlerIceCreatureGadgetRemoveReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        IceCreatureSystem.onRemove(session.getPlayer(), IceCreatureGadgetRemoveReq.parseFrom(payload));
    }
}
