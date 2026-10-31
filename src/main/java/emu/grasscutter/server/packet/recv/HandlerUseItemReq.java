package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.systems.ArtifactTransmuterSystem;
import emu.grasscutter.game.systems.MusicGameBookSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUseItemRsp;

@Opcodes(PacketOpcodes.UseItemReq)
public class HandlerUseItemReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UseItemReq req = UseItemReq.parseFrom(payload);
        GameItem item = session.getPlayer().getInventory().getItemByGuid(req.getGuid());

        // Artifact Transmuter: useOp is NONE. Ack UseItemRsp, then full offer sync (FLIJ + companion).
        if (item != null && item.getItemId() == ArtifactTransmuterSystem.GADGET_ITEM_ID) {
            ArtifactTransmuterSystem.ensureGadget(session.getPlayer());
            session.send(new PacketUseItemRsp(req.getTargetGuid(), item));
            ArtifactTransmuterSystem.sendDataNotify(session.getPlayer());
            return;
        }

        // Repertoire: opens MusicGameMainPage; sync MusicGameBookAllDataNotify.
        if (item != null && item.getItemId() == MusicGameBookSystem.GADGET_ITEM_ID) {
            MusicGameBookSystem.ensureGadget(session.getPlayer());
            session.send(new PacketUseItemRsp(req.getTargetGuid(), item));
            MusicGameBookSystem.sendDataNotify(session.getPlayer());
            return;
        }

        GameItem useItem =
                session
                        .getServer()
                        .getInventorySystem()
                        .useItem(
                                session.getPlayer(),
                                req.getTargetGuid(),
                                req.getGuid(),
                                req.getCount(),
                                req.getOptionIdx(),
                                req.getIsEnterMpDungeonTeam());
        if (useItem != null) {
            session.send(new PacketUseItemRsp(req.getTargetGuid(), useItem));
        } else {
            session.send(new PacketUseItemRsp());
        }
    }
}
