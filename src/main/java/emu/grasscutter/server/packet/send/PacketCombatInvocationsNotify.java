package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CombatInvocationsNotifyOuterClass.CombatInvocationsNotify;
import emu.grasscutter.net.proto.CombatInvokeEntryOuterClass.CombatInvokeEntry;
import java.util.List;
import emu.grasscutter.game.systems.CombatSequence;

public class PacketCombatInvocationsNotify extends BasePacket {

    /**
     * 7.1: the client orders incoming combat invocations by client_sequence_id and drops
     * the entity-move payload of any packet whose sequence does not advance. The proxy (us) owns
     * the sequence on the server -> client direction, so hand out a strictly increasing number.
     */
    private static int nextSeq() {
        return CombatSequence.next();
    }

    public PacketCombatInvocationsNotify(CombatInvokeEntry entry) {
        super(PacketOpcodes.CombatInvocationsNotify, true);

        CombatInvocationsNotify proto =
                CombatInvocationsNotify.newBuilder()
                        .setClientSequenceId(nextSeq())
                        .addInvokeList(entry)
                        .build();

        this.setData(proto);
    }

    public PacketCombatInvocationsNotify(List<CombatInvokeEntry> entries) {
        super(PacketOpcodes.CombatInvocationsNotify, true);

        CombatInvocationsNotify proto =
                CombatInvocationsNotify.newBuilder()
                        .setClientSequenceId(nextSeq())
                        .addAllInvokeList(entries)
                        .build();

        this.setData(proto);
    }
}
