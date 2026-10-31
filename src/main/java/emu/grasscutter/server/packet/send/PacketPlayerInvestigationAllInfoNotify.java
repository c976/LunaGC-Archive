package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.InvestigationOuterClass.Investigation;
import emu.grasscutter.net.proto.InvestigationTargetOuterClass.InvestigationTarget;
import emu.grasscutter.net.proto.PlayerInvestigationAllInfoNotifyOuterClass.PlayerInvestigationAllInfoNotify;
import emu.grasscutter.net.proto.PlayerInvestigationNotifyOuterClass.PlayerInvestigationNotify;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import java.util.Collection;
import java.util.List;

/** Adventurer Handbook Investigation full sync ({@code PlayerInvestigationAllInfoNotify}). */
public class PacketPlayerInvestigationAllInfoNotify extends BasePacket {
    public record InvestigationInfo(int id, int progress, int totalProgress, int state) {}

    public record TargetInfo(
            int investigationId, int questId, int progress, int totalProgress, int state) {}

    public PacketPlayerInvestigationAllInfoNotify(
            Collection<InvestigationInfo> investigations, Collection<TargetInfo> targets) {
        super(PacketOpcodes.PlayerInvestigationAllInfoNotify);
        this.setData(
                PlayerInvestigationAllInfoNotify.newBuilder()
                        .addAllInvestigationList(toProto(investigations))
                        .addAllInvestigationTargetList(toTargetProto(targets))
                        .build());
    }

    /** Also push chapter list alone (PlayerInvestigationNotify). */
    public static BasePacket asChapterNotify(Collection<InvestigationInfo> investigations) {
        BasePacket pkt = new BasePacket(PacketOpcodes.PlayerInvestigationNotify);
        pkt.setData(
                PlayerInvestigationNotify.newBuilder()
                        .addAllInvestigationList(toProto(investigations))
                        .build());
        return pkt;
    }

    /** Also push targets alone (PlayerInvestigationTargetNotify). */
    public static BasePacket asTargetNotify(Collection<TargetInfo> targets) {
        BasePacket pkt = new BasePacket(PacketOpcodes.PlayerInvestigationTargetNotify);
        pkt.setData(
                PlayerInvestigationTargetNotify.newBuilder()
                        .addAllInvestigationTargetList(toTargetProto(targets))
                        .build());
        return pkt;
    }

    private static List<Investigation> toProto(Collection<InvestigationInfo> investigations) {
        if (investigations == null) return List.of();
        return investigations.stream()
                .map(
                        inv ->
                                Investigation.newBuilder()
                                        .setId(inv.id())
                                        .setProgress(inv.progress())
                                        .setTotalProgress(inv.totalProgress())
                                        .setStateValue(inv.state())
                                        .build())
                .toList();
    }

    private static List<InvestigationTarget> toTargetProto(Collection<TargetInfo> targets) {
        if (targets == null) return List.of();
        return targets.stream()
                .map(
                        t ->
                                InvestigationTarget.newBuilder()
                                        .setInvestigationId(t.investigationId())
                                        .setQuestId(t.questId())
                                        .setProgress(t.progress())
                                        .setTotalProgress(t.totalProgress())
                                        .setStateValue(t.state())
                                        .build())
                .toList();
    }
}
