package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CutSceneFinishNotifyOuterClass.CutSceneFinishNotify;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCutSceneEndNotify;

@Opcodes(PacketOpcodes.CutSceneFinishNotify)
public class HandlerCutSceneFinishNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        CutSceneFinishNotify req =
                CutSceneFinishNotify.parseFrom(payload == null ? new byte[0] : payload);
        int cutsceneId = req.getCutsceneId();
        var player = session.getPlayer();
        if (player == null) return;

        // Ack so the client dismisses the cutscene overlay.
        session.send(new PacketCutSceneEndNotify(cutsceneId));

        // A manual GM playback is visual-only. Never complete a quest script or a tower swap.
        if (session.consumeManualCutscene(cutsceneId)) return;

        var scene = player.getScene();
        if (scene != null) {
            scene.getScriptManager().finishCutscene(cutsceneId);
        }

        // Spiral Abyss mid-half waits for its cutscene before swapping teams.
        player.getTowerManager().onMidHalfCutsceneFinished(cutsceneId);
    }
}
