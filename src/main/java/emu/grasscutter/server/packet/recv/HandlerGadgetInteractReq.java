package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.server.game.GameSession;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Collectors;

@Opcodes(PacketOpcodes.GadgetInteractReq)
public class HandlerGadgetInteractReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GadgetInteractReq req = GadgetInteractReq.parseFrom(payload);
        var entity = session.getPlayer().getScene().getEntityById(req.getGadgetEntityId());
        if (entity instanceof emu.grasscutter.game.entity.EntityGadget flower
                && flower.getScene().getBlossomManager() != null
                && flower.getScene().getBlossomManager().hasRewardChest(flower)) {
            // Capture the client's actual C2S bytes before drawing conclusions about the
            // obfuscated Global 7.1 20/40-resin selector. Proto reserialization is not raw wire.
            String fields = req.getAllFields().entrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> entry.getKey().getNumber()))
                    .map(entry -> entry.getKey().getNumber() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
            Grasscutter.getLogger().info(
                    "Ley-line client claim wire uid={} op={} resinCostType={} fields=[{}] payloadHex={}",
                    session.getPlayer().getUid(), req.getOpType(), req.getResinCostType(),
                    fields, HexFormat.of().formatHex(payload));
        }
        if (entity instanceof emu.grasscutter.game.entity.EntityGadget gadget
                && (gadget.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueDustBell
                    || gadget.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueGuizhong
                    || gadget.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueJadeMaterials)) {
            session.getPlayer().interactWith(req.getGadgetEntityId(), req);
            return;
        }

        session.getPlayer().interactWith(req.getGadgetEntityId(), req);
    }
}
