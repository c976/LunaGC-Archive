package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarChangeWeaponSkinReq._AvatarChangeWeaponSkinReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAvatarChangeWeaponSkinRsp;

@Opcodes(PacketOpcodes._AvatarChangeWeaponSkinReq)
public class HandlerAvatarChangeWeaponSkinReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        _AvatarChangeWeaponSkinReq req = _AvatarChangeWeaponSkinReq.parseFrom(payload);

        var player = session.getPlayer();
        var guids = req.getAvatarGuidListList();
        int skinId = req.getWeaponSkinId();

        // The client blocks on the Rsp (with no reply, clicking a skin does nothing), so unlike
        // the (lossy) official capture we must answer.
        boolean success = !guids.isEmpty() && player.hasWeaponSkin(skinId);
        session.send(
                success
                        ? new PacketAvatarChangeWeaponSkinRsp(guids, skinId)
                        : new PacketAvatarChangeWeaponSkinRsp());

        if (success) {
            player.getAvatars().changeWeaponSkin(guids, skinId);
        }
    }
}
