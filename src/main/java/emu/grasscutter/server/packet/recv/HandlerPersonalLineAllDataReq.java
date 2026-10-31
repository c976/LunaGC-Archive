package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCoopDataNotify;
import emu.grasscutter.server.packet.send.PacketPersonalLineAllDataRsp;

@Opcodes(PacketOpcodes.PersonalLineAllDataReq)
public class HandlerPersonalLineAllDataReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // [锚点] 客户端来要传说任务数据了
        session.send(
                new PacketPersonalLineAllDataRsp(
                        session.getPlayer().getQuestManager().getMainQuests().values(),
                        session.getPlayer().getPersonalLineList()));
        session.send(new PacketCoopDataNotify());
    }
}
