package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

/** [v25] 客户端点“使用”后 1~2 秒会补发 25910（空包），原先服务端没有 handler、直接丢掉。
 *  先按“请求”处理：带 seq 回 4162 资料页 + 6326 头像数据。 */
@Opcodes(25910)
public class HandlerMiao25910 extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int seq = 0;
        try {
            if (header != null && header.length > 0) {
                seq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Throwable ignored) {
        }
        Grasscutter.getLogger().debug("MIAO 25910 recv len={} seq={} uid={}", payload == null ? -1 : payload.length, seq, session.getPlayer().getUid());
        try {
            var rsp = new emu.grasscutter.server.packet.send.PacketBeyondPlayerDetailRsp(session.getPlayer());
            rsp.setHeader(PacketHead.newBuilder().setClientSequenceId(seq).setSentMs(System.currentTimeMillis()).build().toByteArray());
            session.send(rsp);
            Grasscutter.getLogger().debug("MIAO 25910 -> reply 4162 seq={}", seq);
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("MIAO 25910 reply fail: {}", t.toString());
        }
    }
}
