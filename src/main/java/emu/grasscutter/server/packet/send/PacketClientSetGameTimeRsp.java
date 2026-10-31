package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientSetGameTimeRspOuterClass.ClientSetGameTimeRsp;

public final class PacketClientSetGameTimeRsp extends BasePacket {
    public PacketClientSetGameTimeRsp(
            Player player, int clientGameTime, int sequence, int retcode) {
        super(PacketOpcodes.ClientSetGameTimeRsp, sequence);

        this.setData(
                ClientSetGameTimeRsp.newBuilder()
                        .setGameTime((int) player.getWorld().getTotalGameTimeMinutes())
                        .setClientGameTime(clientGameTime)
                        .setRetcode(retcode));
    }
}
