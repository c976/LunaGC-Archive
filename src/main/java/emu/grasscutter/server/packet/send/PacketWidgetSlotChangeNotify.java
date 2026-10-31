package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.WidgetPetHelper;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;

public class PacketWidgetSlotChangeNotify extends BasePacket {

    public PacketWidgetSlotChangeNotify(
            WidgetSlotChangeNotifyOuterClass.WidgetSlotChangeNotify proto) {
        super(PacketOpcodes.WidgetSlotChangeNotify);

        this.setData(proto);
    }

    public PacketWidgetSlotChangeNotify(WidgetSlotOpOuterClass.WidgetSlotOp op) {
        super(PacketOpcodes.WidgetSlotChangeNotify);

        WidgetSlotChangeNotifyOuterClass.WidgetSlotChangeNotify proto =
                WidgetSlotChangeNotifyOuterClass.WidgetSlotChangeNotify.newBuilder()
                        .setOp(op)
                        .setSlot(WidgetSlotDataOuterClass.WidgetSlotData.newBuilder().setIsActive(true).build())
                        .build();

        this.setData(proto);
    }

    public PacketWidgetSlotChangeNotify(int materialId) {
        super(PacketOpcodes.WidgetSlotChangeNotify);

        WidgetSlotDataOuterClass.WidgetSlotData.Builder slot =
                WidgetSlotDataOuterClass.WidgetSlotData.newBuilder()
                        .setIsActive(true)
                        .setMaterialId(materialId);

        // Follower pets use the attach-avatar slot, not the quick-use slot.
        if (WidgetPetHelper.hasAbilityGroup(materialId)) {
            slot.setTag(
                    WidgetSlotTagOuterClass.WidgetSlotTag.WidgetSlotTag_WIDGET_SLOT_ATTACH_AVATAR);
        }

        WidgetSlotChangeNotifyOuterClass.WidgetSlotChangeNotify proto =
                WidgetSlotChangeNotifyOuterClass.WidgetSlotChangeNotify.newBuilder()
                        .setOp(WidgetSlotOpOuterClass.WidgetSlotOp.WidgetSlotOp_ATTACH)
                        .setSlot(slot.build())
                        .build();

        this.setData(proto);
    }
}
