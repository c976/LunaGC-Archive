package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.MapAreaChangeNotifyOuterClass.MapAreaChangeNotify;
import emu.grasscutter.net.proto.MapAreaInfoOuterClass.MapAreaInfo;

public class PacketMapAreaChangeNotify extends BasePacket {
    public PacketMapAreaChangeNotify(int mapAreaId, boolean open) {
        super(PacketOpcodes.MapAreaChangeNotify);
        this.setData(
                MapAreaChangeNotify.newBuilder()
                        .addMapAreaInfoList(
                                MapAreaInfo.newBuilder().setMapAreaId(mapAreaId).setIsOpen(open).build())
                        .build());
    }
}
