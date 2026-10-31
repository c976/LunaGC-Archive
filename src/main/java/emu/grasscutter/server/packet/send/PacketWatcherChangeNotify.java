package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WatcherChangeNotifyOuterClass.WatcherChangeNotify;
import java.util.List;

public class PacketWatcherChangeNotify extends BasePacket {
    public PacketWatcherChangeNotify(List<Integer> added, List<Integer> removed) {
        super(PacketOpcodes.WatcherChangeNotify);

        this.setData(
                WatcherChangeNotify.newBuilder()
                        .addAllNewWatcherList(added)
                        .addAllRemovedWatcherList(removed)
                        .build());
    }
}
