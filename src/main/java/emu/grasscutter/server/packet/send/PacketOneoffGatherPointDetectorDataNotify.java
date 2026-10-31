package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.OneoffGatherPointDetectorDataNotifyOuterClass.OneoffGatherPointDetectorDataNotify;
import emu.grasscutter.net.proto.OneoffGatherPointDetectorDataOuterClass.OneoffGatherPointDetectorData;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import java.util.List;

/**
 * Hint circles / material pins used by the gather-point detector widget.
 * Closer to the official material-icon hints than player MapMarks (clover pins).
 */
public class PacketOneoffGatherPointDetectorDataNotify extends BasePacket {

    public PacketOneoffGatherPointDetectorDataNotify(int materialId, List<Position> points) {
        super(PacketOpcodes.OneoffGatherPointDetectorDataNotify);

        var notify = OneoffGatherPointDetectorDataNotify.newBuilder();
        int i = 0;
        for (Position pos : points) {
            if (pos == null) continue;
            ++i;
            notify.addOneoffGatherPointDetectorDataList(
                    OneoffGatherPointDetectorData.newBuilder()
                            .setSceneId(3)
                            .setMaterialId(materialId)
                            .setConfigId(i)
                            .setGroupId(910700000 + i)
                            .setHintRadius(60)
                            .setIsHintValid(true)
                            .setIsAllCollected(false)
                            .setHintCenterPos(
                                    Vector.newBuilder()
                                            .setX(pos.getX())
                                            .setY(pos.getY())
                                            .setZ(pos.getZ())
                                            .build()));
        }
        this.setData(notify.build());
    }
}
