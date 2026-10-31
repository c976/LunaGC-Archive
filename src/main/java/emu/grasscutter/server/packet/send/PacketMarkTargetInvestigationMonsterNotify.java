package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;
import emu.grasscutter.net.proto.MarkTargetInvestigationMonsterNotifyOuterClass.MarkTargetInvestigationMonsterNotify;

public class PacketMarkTargetInvestigationMonsterNotify extends BasePacket {
    public PacketMarkTargetInvestigationMonsterNotify(
            InvestigationMonsterOuterClass.InvestigationMonster investigationMonster) {
        super(PacketOpcodes.MarkTargetInvestigationMonsterNotify);
        var notify =
                MarkTargetInvestigationMonsterNotify.newBuilder()
                        .setInvestigationMonsterId(investigationMonster.getId());
        if (investigationMonster.getInvestigationMonsterDetailListCount() > 0) {
            var detail = investigationMonster.getInvestigationMonsterDetailList(0);
            if (detail.hasMonsterConfig()) {
                // 7.0 sent the config in both of its config fields; 7.1 keeps a single and a list one.
                notify.setGHPPPJJLDJN(detail.getMonsterConfig())
                        .addConfigList(detail.getMonsterConfig());
            }
        }
        this.setData(notify.build());
    }
}
