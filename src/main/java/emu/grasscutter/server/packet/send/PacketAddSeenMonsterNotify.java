package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AddSeenMonsterNotifyOuterClass.AddSeenMonsterNotify;

public class PacketAddSeenMonsterNotify extends BasePacket {
    public PacketAddSeenMonsterNotify(int monsterId) {
        super(PacketOpcodes.AddSeenMonsterNotify);
        var notify = AddSeenMonsterNotify.newBuilder();
        if (monsterId > 0) notify.addMonsterIdList(monsterId);
        this.setData(notify.build());
    }
}
