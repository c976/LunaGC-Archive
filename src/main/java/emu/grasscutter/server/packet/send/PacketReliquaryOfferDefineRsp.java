package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.ArtifactTransmuterSystem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;

/**
 * MDCPAKFIAEG (465) — all.proto:
 *   HBLBDKIBOCG = 5  // version / set index
 *   OLGBLCNIAEF = 15 // schedule 700
 *   retcode = 13
 */
public class PacketReliquaryOfferDefineRsp extends BasePacket {

    public PacketReliquaryOfferDefineRsp(int versionIdx) {
        super(PacketOpcodes.ReliquaryOfferDefineRsp);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 7.1 numbers (7.0: 5 and 15)
        ProtoWire.writeUint32Force(out, 1, versionIdx);
        ProtoWire.writeUint32Force(out, 3, ArtifactTransmuterSystem.SCHEDULE_ID);
        // retcode 0 omitted (proto3 default)
        this.setData(out.toByteArray());
    }
}
