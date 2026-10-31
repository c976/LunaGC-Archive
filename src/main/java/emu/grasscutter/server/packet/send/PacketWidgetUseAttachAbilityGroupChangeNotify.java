package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.WidgetUseAttachAbilityGroupChangeNotifyOuterClass.WidgetUseAttachAbilityGroupChangeNotify;

/** Notifies the client to attach/detach a widget ability group (e.g. Endora / Mini Seelie). */
public class PacketWidgetUseAttachAbilityGroupChangeNotify extends BasePacket {
    public PacketWidgetUseAttachAbilityGroupChangeNotify(int materialId, boolean isAttach) {
        super(PacketOpcodes.WidgetUseAttachAbilityGroupChangeNotify, true);
        this.setData(
                WidgetUseAttachAbilityGroupChangeNotify.newBuilder()
                        .setIsAttach(isAttach)
                        .setMaterialId(materialId)
                        .build());
    }
}
