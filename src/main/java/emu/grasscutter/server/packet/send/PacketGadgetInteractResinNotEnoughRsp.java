/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.GadgetInteractRspOuterClass;
import emu.grasscutter.net.proto.InteractTypeOuterClass;

public class PacketGadgetInteractResinNotEnoughRsp
extends BasePacket {
    public static final int OPCODE = PacketOpcodes.GadgetInteractRsp;

    public PacketGadgetInteractResinNotEnoughRsp(EntityBaseGadget entityBaseGadget) {
        super(OPCODE);
        GadgetInteractRspOuterClass.GadgetInteractRsp.Builder builder = GadgetInteractRspOuterClass.GadgetInteractRsp.newBuilder().setRetcode(660).setInteractType(InteractTypeOuterClass.InteractType.InteractType_INTERACT_OPEN_STATUE);
        if (entityBaseGadget != null) {
            builder.setGadgetEntityId(entityBaseGadget.getId()).setGadgetId(entityBaseGadget.getGadgetId());
        }
        this.setData(builder.build());
    }
}
