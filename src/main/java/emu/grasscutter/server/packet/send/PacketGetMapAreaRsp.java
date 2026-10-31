package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.MapAreaStates;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetMapAreaRspOuterClass.GetMapAreaRsp;
import emu.grasscutter.net.proto.MapAreaInfoOuterClass.MapAreaInfo;

public class PacketGetMapAreaRsp extends BasePacket {

    public PacketGetMapAreaRsp(Player player) {
        super(PacketOpcodes.GetMapAreaRsp);

        // Only the configured areas: the old fixed 1..200 named ids that do not exist and opened both
        // halves of every on/off pair.
        GetMapAreaRsp.Builder p = GetMapAreaRsp.newBuilder();
        MapAreaStates.forPlayer(player)
                .forEach(
                        (id, open) ->
                                p.addMapAreaInfoList(
                                        MapAreaInfo.newBuilder().setMapAreaId(id).setIsOpen(open).build()));

        this.setData(p);
    }
}
