package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.proto.SetReliquaryStarStateReq;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetReliquaryStarStateRsp;
import emu.grasscutter.server.packet.send.PacketStoreItemChangeNotify;


/** SetReliquaryStarStateReq — toggle relic star; also ack so relic UI can close. */
@Opcodes(PacketOpcodes.SetReliquaryStarStateReq)
public class HandlerSetReliquaryStarStateReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = SetReliquaryStarStateReq._SetReliquaryStarStateReq.parseFrom(payload);
        long guid = req.getTargetReliquaryGuid();
        boolean starred = req.getIsRelicStarred();
        GameItem item = session.getPlayer().getInventory().getItemByGuid(guid);
        if (item != null && item.getItemType() == emu.grasscutter.game.inventory.ItemType.ITEM_RELIQUARY) {
            item.setRelicStarred(starred);
            item.save();
            session.send(new PacketStoreItemChangeNotify(item));
        }

        session.send(new PacketSetReliquaryStarStateRsp(guid, starred));
    }
}
