package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NALBKOPFIFDOuterClass.NALBKOPFIFD;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.UnlockPersonalLineReqOuterClass.UnlockPersonalLineReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUnlockPersonalLineRsp;

/**
 * 7.1 has two requests shaped {personal_line_id = 13} (5896 and 21679) and nothing tells which one
 * unlocks a legendary quest, so both land here: a line that is still locked is unlocked, anything
 * else just gets the {personal_line_id, retcode} reply.
 */
@Opcodes(PacketOpcodes.UnlockPersonalLineReq)
public class HandlerUnlockPersonalLineReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        handleRequest(session, PacketOpcodes.UnlockPersonalLineReq, payload);
    }

    static void handleRequest(GameSession session, int opcode, byte[] payload) throws Exception {
        var req = UnlockPersonalLineReq.parseFrom(payload);
        var player = session.getPlayer();
        int id = req.getPersonalLineId();
        var data = GameData.getPersonalLineDataMap().get(id);
        boolean locked = data != null && !player.getPersonalLineList().contains(id);
        Grasscutter.getLogger()
                .info(
                        "PersonalLine req opcode={} uid={} id={} known={} locked={}",
                        opcode,
                        player.getUid(),
                        id,
                        data != null,
                        locked);

        if (!locked) {
            var rsp =
                    NALBKOPFIFD.newBuilder()
                            .setPersonalLineId(id)
                            .setRetcode(data != null ? 0 : Retcode.RET_FAIL.getNumber())
                            .build();
            var packet = new BasePacket(PacketOpcodes.NALBKOPFIFD);
            packet.setData(rsp);
            session.send(packet);
            return;
        }

        player.addPersonalLine(data.getId());
        player.useLegendaryKey(1);

        session.send(new PacketUnlockPersonalLineRsp(data.getId(), 1, data.getChapterId()));
    }
}
