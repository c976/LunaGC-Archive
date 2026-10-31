package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.util.Map;
import emu.grasscutter.net.proto.AllShareCDDataNotifyOuterClass.AllShareCDDataNotify;
import emu.grasscutter.net.proto.ShareCD._ShareCD;
import emu.grasscutter.net.proto.ShareCDInfoOuterClass.ShareCDInfo;

/** S2C AllShareCDDataNotify. */
public class PacketAllShareCDDataNotify extends BasePacket {
    public PacketAllShareCDDataNotify(int shareCdId, int chargeIndex, long cdEndTimeMs, boolean isAll) {
        super(PacketOpcodes.AllShareCDDataNotify);
        this.setData(encode(Map.of(shareCdId, new long[] {chargeIndex, cdEndTimeMs}), isAll));
    }

    public PacketAllShareCDDataNotify(Map<Integer, long[]> shareCdEnds, boolean isAll) {
        super(PacketOpcodes.AllShareCDDataNotify);
        this.setData(encode(shareCdEnds, isAll));
    }

    /**
     * @param shareCdEnds map shareCdId → {chargeIndex, cdEndTimeMs}
     */
    private static AllShareCDDataNotify encode(Map<Integer, long[]> shareCdEnds, boolean isAll) {
        var notify = AllShareCDDataNotify.newBuilder().setIsAll(isAll);
        for (Map.Entry<Integer, long[]> e : shareCdEnds.entrySet()) {
            long[] pair = e.getValue();
            var shareCd =
                    _ShareCD.newBuilder()
                            .setHABMJKBLIMA((int) pair[0]) // charge index
                            .setCdEndTime(pair[1]);
            notify.putShareCdInfoMap(
                    e.getKey(),
                    ShareCDInfo.newBuilder().setShareCdId(e.getKey()).addShareCdList(shareCd).build());
        }
        return notify.build();
    }
}
