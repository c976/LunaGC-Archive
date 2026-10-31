package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

/**
 * 7.1：点头像框“使用”时客户端发 21517 = IEAEDEPOEHM { uint32 DMHGLILCIKB = 9 }（值 = 头像框 id）。
 * 实测日志：12:58:59 起连续 5 条 21517，field9 分别为 100014/100012/100013/100013/100000，
 *            但服务端无 handler（handled=false）被直接丢弃 -> 头像框一直不生效。
 *
 * [v30] 照 6969 的做法：解析 field9 -> setProfileFrameId + save -> 回 25989(带seq) -> 6326/4162。
 */
@Opcodes(21517)
public class HandlerMiao21517 extends PacketHandler {

    private static byte[] head(int seq) {
        return PacketHead.newBuilder()
                .setClientSequenceId(seq)
                .setSentMs(System.currentTimeMillis())
                .build()
                .toByteArray();
    }

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int seq = 0;
        try {
            if (header != null && header.length > 0) {
                seq = PacketHead.parseFrom(header).getClientSequenceId();
            }
        } catch (Throwable ignored) {
        }

        int v = 0;
        try {
            int i = 0;
            while (i < payload.length) {
                int tag = payload[i++] & 0xff;
                int fn = tag >> 3, wt = tag & 7;
                if (wt == 0) {
                    int val = 0, sh = 0;
                    while (i < payload.length) {
                        int x = payload[i++] & 0xff;
                        val |= (x & 0x7f) << sh;
                        sh += 7;
                        if ((x & 0x80) == 0) break;
                    }
                    if (fn == 9) v = val;
                } else if (wt == 2) {
                    int ln = 0, sh = 0;
                    while (i < payload.length) {
                        int x = payload[i++] & 0xff;
                        ln |= (x & 0x7f) << sh;
                        sh += 7;
                        if ((x & 0x80) == 0) break;
                    }
                    i += ln;
                } else {
                    break;
                }
            }
        } catch (Throwable t) {
            Grasscutter.getLogger().warn("MIAO 21517 parse failed: {}", t.toString());
        }

        try {
            var pl = session.getPlayer();
            if (v > 0) {
                Grasscutter.getLogger().debug("MIAO 21517 frame={} oldFrame={} seq={}", v, pl.getProfileFrameId(), seq);
                pl.setProfileFrameId(v);
                pl.save();
            }
        } catch (Throwable t) { Grasscutter.getLogger().warn("MIAO 21517 apply fail: {}", t.toString()); }

        final int fseq = seq;
        new Thread(() -> {
            try { Thread.sleep(40); } catch (InterruptedException ignored) {}

            // 25989 GetPlayerSocialDetailRsp（含当前头像 + 头像框）
            try {
                var pl = session.getPlayer();
                session.send(new emu.grasscutter.server.packet.send.PacketGetPlayerSocialDetailRsp(pl.getSocialDetail(), fseq));
                Grasscutter.getLogger().debug(
                        "MIAO 21517 -> send 25989 head={} frame={} seq={}",
                        pl.getHeadImage(), pl.getProfileFrameId(), fseq);
            } catch (Throwable t) { Grasscutter.getLogger().warn("MIAO 21517 25989 fail: {}", t.toString()); }

            try { Thread.sleep(60); } catch (InterruptedException ignored) {}
            try {
                var bpp = new emu.grasscutter.server.packet.send.PacketBeyondProfilePictureDataNotify(session.getPlayer());
                bpp.setHeader(head(fseq));
                session.send(bpp);
            } catch (Throwable t) {}
            try { Thread.sleep(40); } catch (InterruptedException ignored) {}
            try {
                var rsp = new emu.grasscutter.server.packet.send.PacketBeyondPlayerDetailRsp(session.getPlayer());
                rsp.setHeader(head(fseq));
                session.send(rsp);
                Grasscutter.getLogger().debug("MIAO 21517 -> push 6326+4162 seq={} uid={}", fseq, session.getPlayer().getUid());
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("MIAO 21517 push fail: {}", t.toString());
            }
        }, "miao-21517-v30").start();
    }
}
