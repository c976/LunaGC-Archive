package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;

/** 切到「头像框」页签时客户端发 4179。顺手回一次 22477（头像 + 头像框全量），
 *  这样在页签之间来回切就等于在刷新，不用重新打开更换界面。 */
@Opcodes(PacketOpcodes.GetHeadFrameDataReq)
public class HandlerGetHeadFrameDataReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Grasscutter.getLogger().debug("MIAO 4179 HeadFrame -> refresh 22477 in 400ms");
        new Thread(() -> {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            try {
                HandlerGetProfilePictureDataReq.sendProfileList(session, "HeadFrame4179(+400ms)");
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("MIAO 4179 send 22477 failed: {}", t.toString());
            }
        }, "miao-frame-delay").start();
    }
}
