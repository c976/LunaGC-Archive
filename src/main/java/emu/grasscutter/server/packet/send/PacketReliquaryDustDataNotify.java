package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.systems.ReliquaryDustSystem.PlayerDustState;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

/** Alias of 25355 dust/companion notify. */
public class PacketReliquaryDustDataNotify extends BasePacket {

    public PacketReliquaryDustDataNotify(PlayerDustState state) {
        super(PacketOpcodes.ReliquaryOfferCompanionNotify);
        byte[] data = new PacketReliquaryOfferCompanionNotify(state).getData();
        this.setData(data != null ? data : new byte[0]);
    }
}
