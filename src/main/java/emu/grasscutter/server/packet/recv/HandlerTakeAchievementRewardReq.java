package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeAchievementRewardRsp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Opcodes(PacketOpcodes.TakeAchievementRewardReq)
public class HandlerTakeAchievementRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        List<Integer> ids;
        try {
            ids = readIds(payload);
        } catch (IOException exception) {
            session.send(new PacketTakeAchievementRewardRsp());
            return;
        }
        var player = session.getPlayer();
        if (player == null || player.getAchievements() == null) {
            session.send(new PacketTakeAchievementRewardRsp());
            return;
        }
        var achievements = player.getAchievements();
        synchronized (achievements) {
            // Reject an invalid batch before entering the existing guarded reward boundary.
            if (ids.stream().anyMatch(id -> achievements.isInvalid(id) || !achievements.isRewardLeft(id))) {
                session.send(new PacketTakeAchievementRewardRsp());
                return;
            }
            achievements.takeReward(ids);
        }
    }

    /** Validate the full field-15 request, accepting packed and unpacked repeated uint32 IDs. */
    private static List<Integer> readIds(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0 || payload.length > 4096)
            throw new IOException("Missing or oversized achievement claim");
        var input = CodedInputStream.newInstance(payload);
        var ids = new ArrayList<Integer>();
        var unique = new HashSet<Integer>();
        while (!input.isAtEnd()) {
            int tag = input.readTag();
            if ((tag >>> 3) != 15) {
                if (!input.skipField(tag)) throw new IOException("Unexpected end-group in achievement claim");
                continue;
            }
            if ((tag & 7) == 0) {
                addId(ids, unique, input.readUInt32());
            } else if ((tag & 7) == 2) {
                int limit = input.pushLimit(input.readRawVarint32());
                while (input.getBytesUntilLimit() > 0) addId(ids, unique, input.readUInt32());
                input.popLimit(limit);
            } else {
                throw new IOException("Invalid achievement ID wire type");
            }
        }
        if (ids.isEmpty()) throw new IOException("Missing achievement IDs");
        return ids;
    }

    private static void addId(List<Integer> ids, HashSet<Integer> unique, int id) throws IOException {
        if (id <= 0 || ids.size() >= 64 || !unique.add(id))
            throw new IOException("Invalid or duplicate achievement ID");
        ids.add(id);
    }
}
