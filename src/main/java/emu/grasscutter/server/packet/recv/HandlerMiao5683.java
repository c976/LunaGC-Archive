package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetAllUnlockNameCardRsp;

/** 7.1 实测：切到「名片」页签时客户端发 opcode 5683（= OCLEKCLPLAL {}，空包）。
 *  应答：4400 = JMEBGLMKJAB { repeated uint32 @7 = 名片 id；int32 @12 = retcode }
 *  按用户要求：不秒回，延迟 400ms 再发。 */
@Opcodes(5683)
public class HandlerMiao5683 extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null) return;
        new Thread(() -> {
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            try {
                session.send(new PacketGetAllUnlockNameCardRsp(player));
                Grasscutter.getLogger().debug("MIAO 5683(+400ms) -> reply 4400(名片) uid={}", player.getUid());
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("MIAO 5683 send 4400 failed: {}", t.toString());
            }
        }, "miao-namecard-delay").start();
    }
}
