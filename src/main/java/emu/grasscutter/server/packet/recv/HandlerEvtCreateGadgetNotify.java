package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.ability.EscoffierSkillCookHelper;
import emu.grasscutter.game.ability.IneffaRelayHelper;
import emu.grasscutter.game.ability.VentiSkillObjHelper;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EvtCreateGadgetNotifyOuterClass.EvtCreateGadgetNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.EvtCreateGadgetNotify)
public class HandlerEvtCreateGadgetNotify extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EvtCreateGadgetNotify notify = EvtCreateGadgetNotify.parseFrom(payload);

        var scene = session.getPlayer().getScene();

        if (scene.getEntityById(notify.getEntityId()) != null) {
            return;
        }

        var gadgetId = notify.getConfigId();
        EntityClientGadget gadget =
                switch (gadgetId) {

                    case EntitySolarIsotomaClientGadget.GADGET_ID -> new EntitySolarIsotomaClientGadget(
                            session.getPlayer().getScene(), session.getPlayer(), notify);

                    default -> new EntityClientGadget(
                            session.getPlayer().getScene(), session.getPlayer(), notify);
                };

        session.getPlayer().getScene().onPlayerCreateGadget(gadget);
        IneffaRelayHelper.onClientRelayCreated(session.getPlayer(), gadget);
        // Venti Stormeye / WindBlade: drop leftover server shells once the real client one exists.
        VentiSkillObjHelper.onClientSkillObjCreated(session.getPlayer(), gadget);
        // Escoffier's cooking pot: register the entity and clear the server-side shell.
        EscoffierSkillCookHelper.onCookGadgetCreated(
                session.getPlayer(), gadget.getId(), gadget.getGadgetId());
    }
}
