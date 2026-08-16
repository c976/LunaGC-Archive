package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;

import emu.grasscutter.net.proto.SetReliquaryFavouriteReqOuterClass.SetReliquaryFavouriteReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetReliquaryFavouriteRsp;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;

@Opcodes(PacketOpcodes.SetReliquaryFavouriteReq)
public class HandlerSetReliquaryFavouriteReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetReliquaryFavouriteReq req = SetReliquaryFavouriteReq.parseFrom(payload);

        Player player = session.getPlayer();
        session
                .getServer()
                .getInventorySystem().favouriteEquip(session.getPlayer(), req.getTargetReliquaryGuid(), req.getIsFavourite());
    }
}
