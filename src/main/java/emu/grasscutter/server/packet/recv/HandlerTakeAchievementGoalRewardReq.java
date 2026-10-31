package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeAchievementGoalRewardRsp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Opcodes(PacketOpcodes.TakeAchievementGoalRewardReq)
public class HandlerTakeAchievementGoalRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        List<Integer> ids;
        try {
            ids = readIds(payload);
        } catch (IOException exception) {
            session.send(new PacketTakeAchievementGoalRewardRsp());
            return;
        }
        var player = session.getPlayer();
        if (player == null || player.getAchievements() == null) {
            session.send(new PacketTakeAchievementGoalRewardRsp());
            return;
        }
        // The existing synchronized reward boundary owns eligibility, delivery and persistence.
        player.getAchievements().takeGoalReward(ids);
    }

    /** Validate the entire field-14 request before attempting any category reward. */
    private static List<Integer> readIds(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0 || payload.length > 4096)
            throw new IOException("Missing or oversized achievement goal claim");
        var input = CodedInputStream.newInstance(payload);
        var ids = new ArrayList<Integer>();
        var unique = new HashSet<Integer>();
        while (!input.isAtEnd()) {
            long wireTag = readUInt32(input);
            if (wireTag >>> 3 == 0) throw new IOException("Invalid field number");
            int tag = (int) wireTag;
            if ((tag >>> 3) != 14) {
                skipUnknown(input, tag);
                continue;
            }
            if ((tag & 7) == 0) {
                addId(ids, unique, readUInt32(input));
            } else if ((tag & 7) == 2) {
                long length = readUInt32(input);
                if (length > payload.length) throw new IOException("Oversized packed goal IDs");
                int limit = input.pushLimit((int) length);
                while (input.getBytesUntilLimit() > 0) addId(ids, unique, readUInt32(input));
                input.popLimit(limit);
            } else {
                throw new IOException("Invalid achievement goal ID wire type");
            }
        }
        if (ids.isEmpty()) throw new IOException("Missing achievement goal IDs");
        return ids;
    }

    // CodedInputStream.skipField truncates length varints to 32 bits, so skip unknown fields here.
    private static void skipUnknown(CodedInputStream input, int tag) throws IOException {
        switch (tag & 7) {
            case 0 -> skipVarint64(input);
            case 1 -> input.skipRawBytes(8);
            case 2 -> {
                long length = readUInt32(input);
                if (length > Integer.MAX_VALUE) throw new IOException("Oversized unknown field");
                input.skipRawBytes((int) length);
            }
            case 5 -> input.skipRawBytes(4);
            default -> throw new IOException("Unsupported unknown-field wire type");
        }
    }

    // readRawVarint64 silently drops bits beyond 64; accept at most ten bytes, the last only 0 or 1.
    private static void skipVarint64(CodedInputStream input) throws IOException {
        for (int i = 0; i < 10; i++) {
            int next = input.readRawByte() & 0xff;
            if (i == 9 && next > 1) throw new IOException("Overflowing varint");
            if ((next & 0x80) == 0) return;
        }
        throw new IOException("Overlong varint");
    }

    // Avoid truncating an oversized varint into a valid ID or a short packed-field length.
    private static long readUInt32(CodedInputStream input) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int next = input.readRawByte() & 0xff;
            value |= (long) (next & 0x7f) << shift;
            if ((next & 0x80) == 0 && value <= 0xffffffffL) return value;
        }
        throw new IOException("Overlong achievement goal ID");
    }

    private static void addId(List<Integer> ids, HashSet<Integer> unique, long value) throws IOException {
        if (value <= 0 || value > Integer.MAX_VALUE || ids.size() >= 64)
            throw new IOException("Invalid achievement goal ID");
        int id = (int) value;
        if (!unique.add(id)) throw new IOException("Duplicate achievement goal ID");
        ids.add(id);
    }
}
