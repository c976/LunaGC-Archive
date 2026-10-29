package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientSetGameTimeReqOuterClass.ClientSetGameTimeReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketClientSetGameTimeRsp;
import emu.grasscutter.server.packet.send.PacketPlayerGameTimeNotify;

@Opcodes(PacketOpcodes.ClientSetGameTimeReq)
public class HandlerClientSetGameTimeReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = ClientSetGameTimeReq.parseFrom(payload);
        var player = session.getPlayer();
        var world = player.getWorld();

        long wantMinutes = Integer.toUnsignedLong(req.getGameTime());
        world.changeTime(wantMinutes * 1000L);

        var scene = player.getScene();
        if (scene != null) {
            scene.broadcastPacket(new PacketPlayerGameTimeNotify(player));
        } else {
            player.sendPacket(new PacketPlayerGameTimeNotify(player));
        }

        player.sendPacket(new PacketClientSetGameTimeRsp(player, req.getClientGameTime()));
    }
}
