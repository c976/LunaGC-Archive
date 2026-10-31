package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneReadyReq)
public class HandlerEnterSceneReadyReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) {
        var player = session.getPlayer();

        // Fresh 7.1 can ask for scene-ready while the cold Player.onLogin tail is still running on
        // the worker pool. Resume this response as soon as login initialization completes.
        if (BornIntroGate.deferSceneReadyUntilLoginComplete(session)) {
            return;
        }

        session.send(new PacketEnterScenePeerNotify(player));
        session.send(new PacketEnterSceneReadyRsp(player));
    }
}
