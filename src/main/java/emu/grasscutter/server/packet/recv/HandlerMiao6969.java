package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;

/**
 * 7.1：点“使用”时客户端发 6969 = MLHHHHIAGOF { uint32 NNMPJPNDJMP = 8 }（值 = 头像/头像框 id）。
 *
 * [v23] 客户端会丢弃 clientSequenceId 不匹配的包 -> 回包必须带上本次请求的 seq。
 * [v29] 用户重登才生效 -> 登录序列里有 25989 GetPlayerSocialDetailRsp（含头像id+头像框id）。
 *       Player.getSocialDetail() 读的正是 headImage / profileFrameId，因此“点使用”后
 *       重新发一份 25989 就相当于把新的资料当场推给客户端。
 *       本版：6969 -> 存值 -> 25989(带seq) -> 6326 -> 4162。撤掉 9219（它只会关界面、不更新）。
 */
@Opcodes(6969)
public class HandlerMiao6969 extends PacketHandler {

    private static final int[] FRAME_IDS = {100000, 100011, 100012, 100013, 100014};

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
                    if (fn == 8) v = val;
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
            Grasscutter.getLogger().warn("MIAO 6969 parse failed: {}", t.toString());
        }

        try {
            var pl = session.getPlayer();
            if (v > 0) {
                boolean isFrame = false;
                for (int fid : FRAME_IDS) if (fid == v) isFrame = true;
                Grasscutter.getLogger().debug("MIAO 6969 value={} isFrame={} oldHead={} seq={}", v, isFrame, pl.getHeadImage(), seq);
                if (isFrame) { pl.setProfileFrameId(v); pl.save(); } else { pl.setHeadImage(v); pl.save(); }
            }
        } catch (Throwable t) { Grasscutter.getLogger().warn("MIAO 6969 apply fail: {}", t.toString()); }

        final int fseq = seq;
        new Thread(() -> {
            try { Thread.sleep(40); } catch (InterruptedException ignored) {}

            // [v29] 25989 GetPlayerSocialDetailRsp —— 登录时就是它把“当前头像/头像框”交给客户端的
            try {
                var pl = session.getPlayer();
                var detail = pl.getSocialDetail();
                session.send(new emu.grasscutter.server.packet.send.PacketGetPlayerSocialDetailRsp(detail, fseq));
                Grasscutter.getLogger().debug(
                        "MIAO 6969 -> send 25989 head={} frame={} seq={}",
                        pl.getHeadImage(), pl.getProfileFrameId(), fseq);
            } catch (Throwable t) { Grasscutter.getLogger().warn("MIAO 25989 fail: {}", t.toString()); }

            try { Thread.sleep(60); } catch (InterruptedException ignored) {}
            // 保底：头像数据通知 6326
            try {
                var bpp = new emu.grasscutter.server.packet.send.PacketBeyondProfilePictureDataNotify(session.getPlayer());
                bpp.setHeader(head(fseq));
                session.send(bpp);
            } catch (Throwable t) {}
            try { Thread.sleep(40); } catch (InterruptedException ignored) {}
            // 保底：资料页 4162
            try {
                var rsp = new emu.grasscutter.server.packet.send.PacketBeyondPlayerDetailRsp(session.getPlayer());
                rsp.setHeader(head(fseq));
                session.send(rsp);
                Grasscutter.getLogger().debug("MIAO 6969 -> push 6326+4162 seq={} uid={}", fseq, session.getPlayer().getUid());
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("MIAO 6969 push fail: {}", t.toString());
            }
        }, "miao-6969-v29").start();
    }
}
