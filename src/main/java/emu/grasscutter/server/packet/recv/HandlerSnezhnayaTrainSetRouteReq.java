package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.train.SnezhnayaTrainSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SnezhnayaTrainSetRouteReqOuterClass.SnezhnayaTrainSetRouteReq;
import emu.grasscutter.server.game.GameSession;

/** Snezhnaya train: The player picked where the train should go. */
@Opcodes(PacketOpcodes.SnezhnayaTrainSetRouteReq)
public class HandlerSnezhnayaTrainSetRouteReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SnezhnayaTrainSystem.onSetRoute(session.getPlayer(), SnezhnayaTrainSetRouteReq.parseFrom(payload));
    }
}
