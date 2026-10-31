package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.train.SnezhnayaTrainSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SnezhnayaTrainCallReqOuterClass.SnezhnayaTrainCallReq;
import emu.grasscutter.server.game.GameSession;

/** Snezhnaya train: Grey Eyes called the player's train. */
@Opcodes(PacketOpcodes.SnezhnayaTrainCallReq)
public class HandlerSnezhnayaTrainCallReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SnezhnayaTrainSystem.onCall(session.getPlayer(), SnezhnayaTrainCallReq.parseFrom(payload));
    }
}
