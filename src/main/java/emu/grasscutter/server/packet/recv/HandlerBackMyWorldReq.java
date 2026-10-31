package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketBackMyWorldRsp;

@Opcodes(PacketOpcodes.BackMyWorldReq)
public class HandlerBackMyWorldReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // 房主/客人在多人世界点「返回我的世界」：走多人退出（新世界 + EnterSceneNotify + 踢出客人）
        var mpPlayer = session.getPlayer();
        if (mpPlayer.getWorld() != null && mpPlayer.getWorld().isMultiplayer()) {
            boolean mpResult = session.getServer().getMultiplayerSystem().leaveCoop(mpPlayer);
            session.send(new PacketBackMyWorldRsp(mpResult ? 0 : RetcodeOuterClass.Retcode.RET_FAIL_VALUE));
            return;
        }

        int prevScene = session.getPlayer().getPrevScene();

        // Sanity check for switching between teapot realms
        if (prevScene >= 2000 && prevScene <= 2400) {
            prevScene = 3;
        }

        boolean result =
                session.getServer().getHomeWorldMPSystem().leaveCoop(session.getPlayer(), prevScene);
        session.send(new PacketBackMyWorldRsp(result ? 0 : RetcodeOuterClass.Retcode.RET_FAIL_VALUE));
    }
}
