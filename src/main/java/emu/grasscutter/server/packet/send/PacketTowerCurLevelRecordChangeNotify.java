package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.proto.TowerCurLevelRecordChangeNotifyOuterClass.TowerCurLevelRecordChangeNotify;
import emu.grasscutter.net.proto.TowerCurLevelRecordOuterClass.TowerCurLevelRecord;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tower.TowerManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TowerTeamOuterClass.TowerTeam;
import emu.grasscutter.utils.ProtoWire;
import java.io.ByteArrayOutputStream;
import java.util.List;

public class PacketTowerCurLevelRecordChangeNotify extends BasePacket {
    // Written by hand to force default values onto the wire; the numbers are the generated 7.1 ones.
    private static final int CUR_LEVEL_RECORD =
            TowerCurLevelRecordChangeNotify.CUR_LEVEL_RECORD_FIELD_NUMBER;


    /** Clears the client's "challenge in progress" banner (the "continue challenge?" prompt). */
    public static PacketTowerCurLevelRecordChangeNotify empty() {
        PacketTowerCurLevelRecordChangeNotify packet =
                new PacketTowerCurLevelRecordChangeNotify();
        ByteArrayOutputStream record = new ByteArrayOutputStream();
        ProtoWire.writeTag(record, TowerCurLevelRecord.IS_EMPTY_FIELD_NUMBER, 0);
        ProtoWire.writeVarint(record, 1);
        ByteArrayOutputStream notify = new ByteArrayOutputStream();
        ProtoWire.writeBytes(notify, CUR_LEVEL_RECORD, record.toByteArray());
        packet.setData(notify.toByteArray());
        return packet;
    }

    private PacketTowerCurLevelRecordChangeNotify() {
        super(PacketOpcodes.TowerCurLevelRecordChangeNotify);
    }

    public PacketTowerCurLevelRecordChangeNotify(int curFloorId, int curLevelIndex) {
        this(curFloorId, curLevelIndex, true, null);
    }

    public PacketTowerCurLevelRecordChangeNotify(
            int curFloorId, int curLevelIndex, boolean isUpperPart) {
        this(curFloorId, curLevelIndex, isUpperPart, null);
    }

    public PacketTowerCurLevelRecordChangeNotify(
            int curFloorId, int curLevelIndex, boolean isUpperPart, Player player) {
        super(PacketOpcodes.TowerCurLevelRecordChangeNotify);

        // Build the entire TowerCurLevelRecord on the wire. Proto3 omits bool false, and the
        // client keeps the previous upper/lower half label unless is_upper_part is present — so always
        // force-write is_upper_part (0 = lower half, 1 = upper half). Write it FIRST so a truncated parse still sees it.
        ByteArrayOutputStream record = new ByteArrayOutputStream();

        // is_upper_part — always present (leading)
        ProtoWire.writeTag(record, TowerCurLevelRecord.IS_UPPER_PART_FIELD_NUMBER, 0);
        ProtoWire.writeVarint(record, isUpperPart ? 1 : 0);

        if (player != null) {
            TowerManager tower = player.getTowerManager();
            int buffId = tower.getSelectedTowerBuffId();
            if (buffId > 0) {
                ProtoWire.writeUint32Force(record, TowerCurLevelRecord.BUFF_ID_LIST_FIELD_NUMBER, buffId);
            }
        }

        ProtoWire.writeUint32Force(record, TowerCurLevelRecord.CUR_LEVEL_INDEX_FIELD_NUMBER, curLevelIndex);

        if (player != null) {
            TowerManager tower = player.getTowerManager();
            List<List<Long>> teams = tower.getAbyssTeamGuids();
            for (int i = 0; i < teams.size(); i++) {
                List<Long> guids = teams.get(i);
                if (guids == null || guids.isEmpty()) continue;
                byte[] teamBytes =
                        TowerTeam.newBuilder()
                                .setTowerTeamId(i + 1)
                                .addAllAvatarGuidList(guids)
                                .build()
                                .toByteArray();
                ProtoWire.writeBytes(record, TowerCurLevelRecord.TOWER_TEAM_LIST_FIELD_NUMBER, teamBytes);
            }
        }

        // is_upper_part again at the end (some client builds only read the last occurrence)
        ProtoWire.writeTag(record, TowerCurLevelRecord.IS_UPPER_PART_FIELD_NUMBER, 0);
        ProtoWire.writeVarint(record, isUpperPart ? 1 : 0);

        ProtoWire.writeUint32Force(record, TowerCurLevelRecord.CUR_FLOOR_ID_FIELD_NUMBER, curFloorId);

        // is_empty — force false so the client treats the record as active
        ProtoWire.writeTag(record, TowerCurLevelRecord.IS_EMPTY_FIELD_NUMBER, 0);
        ProtoWire.writeVarint(record, 0);

        ByteArrayOutputStream notify = new ByteArrayOutputStream();
        ProtoWire.writeBytes(notify, CUR_LEVEL_RECORD, record.toByteArray());
        this.setData(notify.toByteArray());
    }
}
