package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ExecuteGadgetLuaReqOuterClass.ExecuteGadgetLuaReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketExecuteGadgetLuaRsp;

@Opcodes(PacketOpcodes.ExecuteGadgetLuaReq)
public class HandlerExecuteGadgetLuaReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ExecuteGadgetLuaReq req = ExecuteGadgetLuaReq.parseFrom(payload);

        Player player = session.getPlayer();
        GameEntity entity = player.getScene().getEntities().get(req.getSourceEntityId());

        int result = 1;
        if (entity instanceof EntityGadget gadget) {
            if (gadget.getContent() instanceof emu.grasscutter.game.entity.gadget.GadgetLiyueFoodOffering offering)
                result = req.getParam1() == 201 && offering.submit(player) == 0 ? 0 : 1;
            else result = gadget.onClientExecuteRequest(req.getParam1(), req.getParam2(), req.getParam3());
        }

        player.sendPacket(new PacketExecuteGadgetLuaRsp(result));
    }
}
