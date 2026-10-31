package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.ArtifactTransmuterSystem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProtoWire;

import java.io.ByteArrayOutputStream;

/**
 * JACMJEDMBGL (23850) — all.proto:
 *   OLGBLCNIAEF = 9 // schedule 700
 *   retcode = 8
 */
public class PacketReliquaryOfferExtractRsp extends BasePacket {

    public PacketReliquaryOfferExtractRsp() {
        super(PacketOpcodes.ReliquaryOfferExtractRsp);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProtoWire.writeUint32Force(out, 12, ArtifactTransmuterSystem.SCHEDULE_ID); // 7.0: 9
        this.setData(out.toByteArray());
    }
}
