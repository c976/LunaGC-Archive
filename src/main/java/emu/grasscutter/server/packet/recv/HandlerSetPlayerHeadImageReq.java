package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerHeadImageRsp;

@Opcodes(PacketOpcodes.SetPlayerHeadImageReq)
public class HandlerSetPlayerHeadImageReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // 7.1 SetPlayerHeadImageReq (CmdId 6969) carries the profile picture id on field 8.
        int id = readProfilePictureId(payload);
        session.getPlayer().setHeadImage(id);
        session.send(new PacketSetPlayerHeadImageRsp(session.getPlayer()));
    }

    private static int readProfilePictureId(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) {
            return 0;
        }
        CodedInputStream in = CodedInputStream.newInstance(payload);
        int id = 0;
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            int field = tag >>> 3;
            int wire = tag & 7;
            if (field == 8 && wire == 0) {
                id = in.readUInt32();
            } else if (!in.skipField(tag)) {
                break;
            }
        }
        return id;
    }
}
