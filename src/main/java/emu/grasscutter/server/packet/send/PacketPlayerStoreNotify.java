package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.GAME_OPTIONS;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.systems.ArtifactTransmuterSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemOuterClass.Item;
import emu.grasscutter.net.proto.PlayerStoreNotifyOuterClass.PlayerStoreNotify;
import emu.grasscutter.net.proto.StoreTypeOuterClass.StoreType;

public class PacketPlayerStoreNotify extends BasePacket {

    public PacketPlayerStoreNotify(Player player) {
        super(PacketOpcodes.PlayerStoreNotify);

        this.buildHeader(2);

        PlayerStoreNotify.Builder p =
                PlayerStoreNotify.newBuilder()
                        .setStoreType(StoreType.StoreType_STORE_PACK)
                        .setWeightLimit(GAME_OPTIONS.inventoryLimits.all);

        for (GameItem item : player.getInventory()) {
            Item itemProto = item.toProto();
            p.addItemList(itemProto);
        }

        this.setData(p.build());

        // Official login burst near StoreWeightLimit: FLIJ Offer + empty companion.
        // Delay must outlast cold-start onLogin. Use isConnected (not isActive): ACTIVE is only
        // set at the very end of onLogin, and isActive()-at-1s previously skipped → TxtItemName.
        ArtifactTransmuterSystem.clearLoginOfferFlag(player.getUid());
        Grasscutter.getGameServer()
                .getScheduler()
                .scheduleDelayedTask(
                        () -> {
                            try {
                                if (player.getSession() != null && player.getSession().isConnected()) {
                                    ArtifactTransmuterSystem.sendLoginNotifyOnce(player);
                                } else {
                                    Grasscutter.getLogger()
                                            .warn(
                                                    "ArtifactTransmuter login Offer skipped uid={} (no session)",
                                                    player.getUid());
                                }
                            } catch (Throwable t) {
                                Grasscutter.getLogger()
                                        .warn("ArtifactTransmuter login Offer failed uid={}: {}", player.getUid(), t.toString());
                            }
                        },
                        8);
    }
}
