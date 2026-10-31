package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.SetWidgetSlotReq)
public class HandlerSetWidgetSlotReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetWidgetSlotReqOuterClass.SetWidgetSlotReq req =
                SetWidgetSlotReqOuterClass.SetWidgetSlotReq.parseFrom(payload);

        Player player = session.getPlayer();
        int previousMaterialId = player.getWidgetId();
        boolean attach = req.getOp() == WidgetSlotOpOuterClass.WidgetSlotOp.WidgetSlotOp_ATTACH;

        emu.grasscutter.Grasscutter.getLogger()
                .info(
                        "SetWidgetSlotReq uid={} op={} materialId={} prevWidget={}",
                        player.getUid(),
                        req.getOp(),
                        req.getMaterialId(),
                        previousMaterialId);

        // DETACH must clear the slot; previously widgetId was always overwritten with materialId.
        if (attach) {
            player.setWidgetId(req.getMaterialId());
        } else {
            player.setWidgetId(0);
        }

        // WidgetSlotChangeNotify op & slot key
        session.send(
                new PacketWidgetSlotChangeNotify(
                        WidgetSlotOpOuterClass.WidgetSlotOp.WidgetSlotOp_DETACH));

        // only attaching the widget can set it
        if (attach) {
            // WidgetSlotChangeNotify slot
            session.send(new PacketWidgetSlotChangeNotify(req.getMaterialId()));
        }

        // SetWidgetSlotRsp
        session.send(new PacketSetWidgetSlotRsp(req.getMaterialId()));

        // Follower pets (Endora, mini seelies): optional helper may be absent from some jar builds.
        tryInvokeWidgetPetHelper(player, previousMaterialId, player.getWidgetId());

        // Repertoire: equipping also primes MusicGameBook data for quick-use / UI.
        if (attach && req.getMaterialId() == emu.grasscutter.game.systems.MusicGameBookSystem.GADGET_ITEM_ID) {
            emu.grasscutter.game.systems.MusicGameBookSystem.sendDataNotify(player);
        }
        // Artifact Transmuter: equipping primes Offer data (avoids TxtItemName if opened without UseItem).
        if (attach && req.getMaterialId() == emu.grasscutter.game.systems.ArtifactTransmuterSystem.GADGET_ITEM_ID) {
            emu.grasscutter.game.systems.ArtifactTransmuterSystem.sendDataNotify(player);
        }
    }

    private static void tryInvokeWidgetPetHelper(Player player, int previousMaterialId, int widgetId) {
        try {
            Class<?> helper = Class.forName("emu.grasscutter.game.player.WidgetPetHelper");
            helper.getMethod("onWidgetSlotChange", Player.class, int.class, int.class)
                    .invoke(null, player, previousMaterialId, widgetId);
        } catch (Throwable ignored) {
            // optional
        }
    }
}
