package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WatcherAllDataNotifyOuterClass.WatcherAllDataNotify;
import java.util.List;

public class PacketWatcherAllDataNotify extends BasePacket {
    public PacketWatcherAllDataNotify(List<Integer> watcherIds) {
        super(PacketOpcodes.WatcherAllDataNotify);

        this.setData(WatcherAllDataNotify.newBuilder().addAllWatcherList(watcherIds).build());
    }
}
