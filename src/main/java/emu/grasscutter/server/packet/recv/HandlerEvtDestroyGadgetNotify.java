package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.ability.EscoffierSkillCookHelper;
import emu.grasscutter.game.ability.IneffaRelayHelper;
import emu.grasscutter.game.entity.EntityClientGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EvtDestroyGadgetNotifyOuterClass.EvtDestroyGadgetNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.EvtDestroyGadgetNotify)
public class HandlerEvtDestroyGadgetNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EvtDestroyGadgetNotify notify = EvtDestroyGadgetNotify.parseFrom(payload);

        int entityId = notify.getEntityId();
        int gadgetId = 0;
        GameEntity existing = session.getPlayer().getScene().getEntityById(entityId);
        if (existing instanceof EntityClientGadget clientGadget) {
            gadgetId = clientGadget.getGadgetId();
        }

        session.getPlayer().getScene().onPlayerDestroyGadget(entityId);
        if (gadgetId > 0) {
            IneffaRelayHelper.onClientGadgetDestroyed(session.getPlayer(), entityId, gadgetId);
        }
        // Escoffier's cooking pot destroyed: schedule the fallback payout, still preferring SkillCookReq.
        EscoffierSkillCookHelper.onCookGadgetDestroyed(session.getPlayer(), entityId);
    }
}
