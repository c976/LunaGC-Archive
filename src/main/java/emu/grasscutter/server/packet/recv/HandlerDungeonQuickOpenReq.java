package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.net.proto.DungeonQuickOpenReq;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;

/**
 * Handbook preparation "quick open" path. Read by hand so a malformed body still opens the
 * handbook; the field number comes from the generated _DungeonQuickOpenReq (1 in 7.0, 7 in 7.1).
 */
@Opcodes(PacketOpcodes.DungeonQuickOpenReq)
public class HandlerDungeonQuickOpenReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int entryConfigId = 0;
        if (payload != null && payload.length > 0) {
            try {
                CodedInputStream input = CodedInputStream.newInstance(payload);
                while (!input.isAtEnd()) {
                    int tag = input.readTag();
                    int field = tag >>> 3;
                    if (field == DungeonQuickOpenReq._DungeonQuickOpenReq.DUNGEON_ENTRY_CONFIG_ID_FIELD_NUMBER) {
                        entryConfigId = input.readUInt32();
                        break;
                    }
                    input.skipField(tag);
                }
            } catch (Exception ignored) {
            }
        }
        DomainDungeonHelper.handleQuickOpen(session.getPlayer(), entryConfigId);
        session.send(new BasePacket(PacketOpcodes.DungeonQuickOpenRsp));
    }
}
