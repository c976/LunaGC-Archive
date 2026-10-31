package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.systems.MusicGameBookSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.QuickUseWidgetRspOuterClass.QuickUseWidgetRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.QuickUseWidgetReq)
public class HandlerQuickUseWidgetReq extends PacketHandler {
    /*
     * WARNING: with the consuming of material widget ( Example: bomb ),
     * this is just a implement designed to the decreasing of count
     *
     * ### Known Bug: No effects after using item but decrease. ###
     *
     * If you know which Packet could make the effects, feel free to contribute!
     * */
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        QuickUseWidgetRsp.Builder proto = QuickUseWidgetRsp.newBuilder();
        Player pl = session.getPlayer();
        synchronized (pl) {
            int materialId = pl.getWidgetId();

            // Repertoire: non-consumable UI widget — open repertoire, do not remove item.
            if (materialId == MusicGameBookSystem.GADGET_ITEM_ID) {
                MusicGameBookSystem.sendDataNotify(pl);
                proto.setRetcode(0).setMaterialId(materialId);
                BasePacket rsp = new BasePacket(PacketOpcodes.QuickUseWidgetRsp);
                rsp.setData(proto);
                session.send(rsp);
                return;
            }

            // Artifact Transmuter: non-consumable — sync Offer data (fixes TxtItemName placeholders).
            if (materialId == emu.grasscutter.game.systems.ArtifactTransmuterSystem.GADGET_ITEM_ID) {
                emu.grasscutter.game.systems.ArtifactTransmuterSystem.ensureGadget(pl);
                emu.grasscutter.game.systems.ArtifactTransmuterSystem.sendDataNotify(pl);
                proto.setRetcode(0).setMaterialId(materialId);
                BasePacket rsp = new BasePacket(PacketOpcodes.QuickUseWidgetRsp);
                rsp.setData(proto);
                session.send(rsp);
                return;
            }

            Inventory inventory = session.getPlayer().getInventory();
            InventoryTab inventoryTab = inventory.getInventoryTab(ItemType.ITEM_MATERIAL);
            GameItem item = inventoryTab.getItemById(materialId);
            if (item == null || item.getCount() <= 0) {
                proto.setRetcode(1).setMaterialId(materialId);
                BasePacket rsp = new BasePacket(PacketOpcodes.QuickUseWidgetRsp);
                rsp.setData(proto);
                session.send(rsp);
                return;
            }
            // Widgets that do not stack (Grey Eyes 220133, the gadgets and devices) are reusable; only
            // stackable ones such as bombs are spent on use.
            if (item.getItemData() == null || item.getItemData().getStackLimit() <= 1) {
                Grasscutter.getLogger().debug("[Widget] quick use of reusable widget {}", materialId);
                proto.setRetcode(0).setMaterialId(materialId);
                BasePacket rsp = new BasePacket(PacketOpcodes.QuickUseWidgetRsp);
                rsp.setData(proto);
                session.send(rsp);
                return;
            }
            int remain = item.getCount();
            if (remain > 0) {
                remain--;
                if (remain <= 0) {
                    proto.setRetcode(1);
                } else {
                    proto.setRetcode(0);
                }
                proto.setMaterialId(materialId);
                inventory.removeItem(item, 1); // decrease count
                BasePacket rsp = new BasePacket(PacketOpcodes.QuickUseWidgetRsp);
                rsp.setData(proto);
                session.send(rsp);
                Grasscutter.getLogger().warn("class has no effects in the game, feel free to implement it");
                // but no effects in the game, feel free to implement it!
            }
        }
    }
}
