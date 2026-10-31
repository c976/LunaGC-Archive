package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerSetPauseReqOuterClass.PlayerSetPauseReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.born.BornIntroGate;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerSetPauseRsp;

@Opcodes(PacketOpcodes.PlayerSetPauseReq)
public class HandlerPlayerSetPauseReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = PlayerSetPauseReq.parseFrom(payload);
        var player = session.getPlayer();
        var world = player.getWorld();

        // The native fresh-player intro toggles pause before the first World exists. These requests
        // are valid and their second false->true cycle is the 7.1 intro handoff boundary.
        if (world == null) {
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_SUCC));
            BornIntroGate.notePause(session, req.getIsPaused());
            return;
        }

        if (player.isInMultiplayer()) {
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_FAIL));
        } else {
            world.setPaused(req.getIsPaused());
            session.send(new PacketPlayerSetPauseRsp(Retcode.RET_SUCC));
        }

        // [喵喵 v11] 客户端只有【第一次】打开更换界面才发 27037，之后不再发；
        // 但每次打开界面必发 PlayerSetPauseReq -> 挂这里才能做到“每次开界面都推”。
        if (req.getIsPaused()) {
            miaoPushProfile(session);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<Integer, Long> MIAO_LAST_PUSH =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static void miaoPushProfile(GameSession session) {
        var player = session.getPlayer();
        if (player == null) return;
        int uid = player.getUid();
        long now = System.currentTimeMillis();
        Long last = MIAO_LAST_PUSH.get(uid);
        if (last != null && now - last < 3000) return; // 3 秒内重复开界面只推一轮
        MIAO_LAST_PUSH.put(uid, now);
        new Thread(
                        () -> {
                            for (int i = 0; i < 1; i++) {
                                try {
                                    Thread.sleep(i == 0 ? 500 : 1000);
                                } catch (InterruptedException ignored) {
                                    return;
                                }
                                try {
                                    HandlerGetProfilePictureDataReq.sendProfileList(
                                            session, "setpause+" + (500 + i * 1000) + "ms");
                                } catch (Throwable t) {
                                    break;
                                }
                            }
                        },
                        "miao-pause-profile")
                .start();
    }
}
