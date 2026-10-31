package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.systems.ArtifactTransmuterSystem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;

/** ReliquaryOffer extract / progress submit. */
@Opcodes(PacketOpcodes.ReliquaryOfferExtractReq)
public class HandlerReliquaryOfferExtractReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ArtifactTransmuterSystem.handleExtract(session.getPlayer(), payload);
    }
}
