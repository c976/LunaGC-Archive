package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarTeamAllDataNotify;
import emu.grasscutter.server.packet.send.PacketAvatarTeamUpdateNotify;
import emu.grasscutter.server.packet.send.PacketMoveTeamRsp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MoveTeamReq wire format (observed):
 *   field 7  varint = src (1-based slot / team id)
 *   field 15 varint = dst
 * hex example: 38 02 78 01 => src=2 dst=1
 *
 * Client slots are team ids. Reorder by moving TeamInfo contents among
 * numerically sorted ids, then notify.
 */
@Opcodes(PacketOpcodes._MoveTeamReq)
public class HandlerMoveTeamReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }

        int[] pair = parseSrcDst(payload);
        Grasscutter.getLogger()
                .info(
                        "MoveTeamReq hex={} src={} dst={}",
                        toHex(payload),
                        pair == null ? -1 : pair[0],
                        pair == null ? -1 : pair[1]);

        TeamManager tm = player.getTeamManager();
        List<Integer> order = sortedIds(tm);
        if (pair != null) {
            order = moveContentsByIndex(tm, pair[0], pair[1]);
            try {
                player.save();
            } catch (Throwable t) {
                Grasscutter.getLogger().warn("MoveTeam save: {}", t.toString());
            }
        }

        // Refresh team map + full team UI data
        player.sendPacket(new PacketAvatarTeamUpdateNotify(player));
        player.sendPacket(new PacketAvatarTeamAllDataNotify(player));
        player.sendPacket(new PacketMoveTeamRsp(order));
    }

    /** Prefer field 7 then field 15 (observed); fallback any two varints. */
    private static int[] parseSrcDst(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            UnknownFieldSet ufs = UnknownFieldSet.parseFrom(payload);
            Integer src = firstVarint(ufs, 7);
            Integer dst = firstVarint(ufs, 15);
            if (src != null && dst != null) {
                return new int[] {src, dst};
            }

            List<Integer> vals = new ArrayList<>();
            List<Integer> fields = new ArrayList<>(ufs.asMap().keySet());
            Collections.sort(fields);
            for (Integer fn : fields) {
                UnknownFieldSet.Field f = ufs.asMap().get(fn);
                if (f.getVarintList() != null) {
                    for (Long v : f.getVarintList()) {
                        vals.add(v.intValue());
                    }
                }
            }
            if (vals.size() >= 2) {
                return new int[] {vals.get(0), vals.get(1)};
            }

            vals.clear();
            CodedInputStream in = CodedInputStream.newInstance(payload);
            while (!in.isAtEnd()) {
                int tag = in.readTag();
                if ((tag & 7) == 0) {
                    vals.add((int) in.readRawVarint64());
                } else {
                    in.skipField(tag);
                }
            }
            if (vals.size() >= 2) {
                return new int[] {vals.get(0), vals.get(1)};
            }
        } catch (Exception e) {
            Grasscutter.getLogger().warn("MoveTeamReq parse: {}", e.toString());
        }
        return null;
    }

    private static Integer firstVarint(UnknownFieldSet ufs, int field) {
        UnknownFieldSet.Field f = ufs.getField(field);
        if (f.getVarintList() == null || f.getVarintList().isEmpty()) {
            return null;
        }
        return f.getVarintList().get(0).intValue();
    }

    private static List<Integer> sortedIds(TeamManager tm) {
        List<Integer> ids = new ArrayList<>(tm.getTeams().keySet());
        Collections.sort(ids);
        return ids;
    }

    /**
     * src/dst are 1-based indices into the UI list (= sorted team ids for normal
     * accounts with teams 1..N). Moves TeamInfo objects between those id slots.
     */
    private static List<Integer> moveContentsByIndex(TeamManager tm, int src1, int dst1) {
        LinkedHashMap<Integer, TeamInfo> map = tm.getTeams();
        List<Integer> ids = sortedIds(tm);
        int from = src1 - 1;
        int to = dst1 - 1;
        if (from < 0 || from >= ids.size() || to < 0 || to >= ids.size() || from == to) {
            // Also allow raw team-id addressing when values equal existing ids
            int fromId = ids.indexOf(src1);
            int toId = ids.indexOf(dst1);
            if (fromId >= 0 && toId >= 0 && fromId != toId) {
                from = fromId;
                to = toId;
            } else {
                Grasscutter.getLogger()
                        .info("MoveTeam skip src={} dst={} ids={}", src1, dst1, ids);
                return ids;
            }
        }

        int curId = tm.getCurrentTeamId();
        TeamInfo curInfo = map.get(curId);

        List<TeamInfo> infos = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            infos.add(map.get(id));
        }

        TeamInfo moved = infos.remove(from);
        if (to > infos.size()) {
            to = infos.size();
        }
        infos.add(to, moved);

        // Rebuild map in sorted id order with new contents
        LinkedHashMap<Integer, TeamInfo> neu = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            neu.put(ids.get(i), infos.get(i));
        }
        map.clear();
        map.putAll(neu);

        if (curInfo != null) {
            for (int i = 0; i < ids.size(); i++) {
                if (infos.get(i) == curInfo) {
                    setCurrentTeamIdQuiet(tm, ids.get(i));
                    break;
                }
            }
        }

        List<String> summary = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            TeamInfo ti = infos.get(i);
            int n = (ti == null || ti.getAvatars() == null) ? 0 : ti.getAvatars().size();
            summary.add(ids.get(i) + ":" + n);
        }
        Grasscutter.getLogger()
                .info(
                        "MoveTeam {}→{} slots={} cur={}",
                        src1,
                        dst1,
                        summary,
                        tm.getCurrentTeamId());
        return new ArrayList<>(map.keySet());
    }

    private static void setCurrentTeamIdQuiet(TeamManager tm, int teamId) {
        try {
            java.lang.reflect.Method m =
                    TeamManager.class.getDeclaredMethod("setCurrentTeamId", int.class);
            m.setAccessible(true);
            m.invoke(tm, teamId);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            java.lang.reflect.Field f = TeamManager.class.getDeclaredField("currentTeamIndex");
            f.setAccessible(true);
            f.setInt(tm, teamId);
        } catch (ReflectiveOperationException ex) {
            Grasscutter.getLogger().warn("MoveTeam setCurrentTeamId failed: {}", ex.toString());
        }
    }

    private static String toHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
