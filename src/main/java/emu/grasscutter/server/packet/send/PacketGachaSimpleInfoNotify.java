/*
 * Decompiled with CFR 0.152.
 */
package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GachaSimpleInfoNotifyOuterClass.GachaSimpleInfoNotify;
import emu.grasscutter.net.packet.BasePacket;

public class PacketGachaSimpleInfoNotify
extends BasePacket {
    public PacketGachaSimpleInfoNotify(boolean bl) {
        super(PacketOpcodes.GachaSimpleInfoNotify);
        this.setData(GachaSimpleInfoNotify.newBuilder().setIsNew(bl).build());
    }
}
