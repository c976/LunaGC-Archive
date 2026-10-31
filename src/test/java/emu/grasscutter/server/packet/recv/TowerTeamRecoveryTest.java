package emu.grasscutter.server.packet.recv;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.proto.TowerTeamOuterClass.TowerTeam;
import emu.grasscutter.net.proto.TowerTeamSelectReqOuterClass.TowerTeamSelectReq;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins TowerTeamSelectReq's field numbers to what the 7.1 client writes.
 *
 * <p>A dump that reads fields out of the client's parse routine alone loses this message's team
 * list - the client only ever sends it - and the abyss then silently gets no team. In the 7.1 dump
 * tower_team_list is field 6 and floor_id field 3 (7.0 had them at 8 and 9).
 *
 * <p>These cases build the payload by hand at those numbers rather than through the generated
 * builder, so a regeneration that dropped tower_team_list again would fail here instead of in game.
 */
public class TowerTeamRecoveryTest {

    private static final long AMBER = 4294967297L;
    private static final long KAEYA = 4294967298L;
    private static final long LISA = 4294967299L;
    private static final long BARBARA = 4294967300L;

    private static byte[] team(int teamId, long... guids) {
        var b = TowerTeam.newBuilder().setTowerTeamId(teamId);
        for (long g : guids) b.addAvatarGuidList(g);
        return b.build().toByteArray();
    }

    /** The request as the 7.1 client lays it out: teams at field 6, floor at field 3. */
    private static TowerTeamSelectReq onTheWire(int floorId, byte[]... teams) throws Exception {
        var baos = new ByteArrayOutputStream();
        var cos = CodedOutputStream.newInstance(baos);
        for (byte[] t : teams) cos.writeByteArray(6, t);
        cos.writeUInt32(3, floorId);
        cos.flush();
        return TowerTeamSelectReq.parseFrom(baos.toByteArray());
    }

    @Test
    @DisplayName("reads both halves of a two-team floor off the wire")
    public void readsTwoTeams() throws Exception {
        var req = onTheWire(1024, team(1, AMBER, KAEYA), team(2, LISA, BARBARA));

        assertEquals(1024, req.getFloorId());
        assertTrue(
                req.getUnknownFields().asMap().isEmpty(), "nothing left unparsed in the request");
        assertEquals(2, req.getTowerTeamListCount());
        assertEquals(List.of(AMBER, KAEYA), req.getTowerTeamList(0).getAvatarGuidListList());
        assertEquals(List.of(LISA, BARBARA), req.getTowerTeamList(1).getAvatarGuidListList());
        assertEquals(1, req.getTowerTeamList(0).getTowerTeamId());
        assertEquals(2, req.getTowerTeamList(1).getTowerTeamId());
    }

    @Test
    @DisplayName("keeps the wire order, which is what picks the second half")
    public void keepsWireOrder() throws Exception {
        // TowerManager enters the halves by index, so reordering here would seat the wrong team.
        var req = onTheWire(1039, team(2, LISA, BARBARA), team(1, AMBER, KAEYA));
        assertEquals(List.of(LISA, BARBARA), req.getTowerTeamList(0).getAvatarGuidListList());
        assertEquals(List.of(AMBER, KAEYA), req.getTowerTeamList(1).getAvatarGuidListList());
    }

    @Test
    @DisplayName("a one-team floor reads back as one team")
    public void readsOneTeam() throws Exception {
        var req = onTheWire(1001, team(1, AMBER, KAEYA, LISA, BARBARA));
        assertEquals(1, req.getTowerTeamListCount());
        assertEquals(4, req.getTowerTeamList(0).getAvatarGuidListCount());
    }

    @Test
    @DisplayName("floor_id is field 3 and not confused with the team list")
    public void floorIdUnchanged() throws Exception {
        var req = onTheWire(1025);
        assertEquals(1025, req.getFloorId());
        assertEquals(0, req.getTowerTeamListCount());
    }

    @Test
    @DisplayName("the mid-chamber team-change notify still builds and serialises to nothing")
    public void middleLevelChangeTeamNotifyLoads() {
        // 7.0 does not name this message, so it keeps its 6.7 class - and a restored 6.7 class is
        // exactly where a corrupt embedded descriptor bites, at first load rather than at build.
        // Its body is empty by design; only the CmdId carries meaning, and that is still unknown.
        var proto =
                emu.grasscutter.net.proto.TowerMiddleLevelChangeTeamNotifyOuterClass
                        .TowerMiddleLevelChangeTeamNotify.newBuilder()
                        .build();
        assertEquals(0, proto.toByteArray().length);
    }
}
