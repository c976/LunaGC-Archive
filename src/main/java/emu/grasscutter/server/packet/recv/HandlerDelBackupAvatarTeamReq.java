package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DelBackupAvatarTeamReqOuterClass.DelBackupAvatarTeamReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DelBackupAvatarTeamReq)
public class HandlerDelBackupAvatarTeamReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // 7.1 puts the team id in field 4 on the wire, while the descriptor's
        // DelBackupAvatarTeamReq declares backup_avatar_team_id as field 9. That is the same drift
        // SetUpAvatarTeamReq works around with its XOR-encoded team id. Parsing with the generated
        // class alone therefore reads 0 and would try to dissolve a team that does not exist.
        int teamId = readFieldVarint(payload, 4);
        if (teamId <= 0) {
            teamId = DelBackupAvatarTeamReq.parseFrom(payload).getBackupAvatarTeamId();
        }
        session.getPlayer().getTeamManager().removeCustomTeam(teamId);
    }

    /** Reads a single varint field out of raw protobuf bytes, or 0 when it is absent. */
    private static int readFieldVarint(byte[] payload, int wantField) {
        if (payload == null) return 0;
        int i = 0;
        while (i < payload.length) {
            int key = payload[i++] & 0xFF;
            int field = key >> 3;
            int wire = key & 7;
            if (wire == 0) {
                int value = 0;
                int shift = 0;
                while (i < payload.length) {
                    int b = payload[i++] & 0xFF;
                    value |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) break;
                    shift += 7;
                }
                if (field == wantField) return value;
            } else if (wire == 2) {
                int len = 0;
                int shift = 0;
                while (i < payload.length) {
                    int b = payload[i++] & 0xFF;
                    len |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) break;
                    shift += 7;
                }
                i += len;
            } else if (wire == 5) {
                i += 4;
            } else if (wire == 1) {
                i += 8;
            } else {
                return 0;
            }
        }
        return 0;
    }
}
