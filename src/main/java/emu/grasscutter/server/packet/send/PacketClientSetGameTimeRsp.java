package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientSetGameTimeRspOuterClass.ClientSetGameTimeRsp;

public class PacketClientSetGameTimeRsp extends BasePacket {

    public PacketClientSetGameTimeRsp(Player player, int clientGameTime) {
        super(PacketOpcodes.ClientSetGameTimeRsp);

        ClientSetGameTimeRsp proto =
                ClientSetGameTimeRsp.newBuilder()
                        .setGameTime((int) player.getWorld().getTotalGameTimeMinutes())
                        .setClientGameTime(clientGameTime)
                        .build();

        this.setData(proto);
    }
}
