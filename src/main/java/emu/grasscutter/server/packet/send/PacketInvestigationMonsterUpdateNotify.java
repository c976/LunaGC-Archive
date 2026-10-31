package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;
import emu.grasscutter.net.proto.InvestigationMonsterUpdateNotifyOuterClass.InvestigationMonsterUpdateNotify;

public class PacketInvestigationMonsterUpdateNotify extends BasePacket {
    public PacketInvestigationMonsterUpdateNotify(
            InvestigationMonsterOuterClass.InvestigationMonster investigationMonster) {
        super(PacketOpcodes.InvestigationMonsterUpdateNotify);
        this.setData(
                InvestigationMonsterUpdateNotify.newBuilder()
                        .setInvestigationMonster(investigationMonster)
                        .build());
    }
}
