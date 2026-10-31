package emu.grasscutter.server.packet.send;

import static emu.grasscutter.config.Configuration.INVENTORY_LIMITS;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.StoreTypeOuterClass.StoreType;
import emu.grasscutter.net.proto.StoreWeightLimitNotifyOuterClass.StoreWeightLimitNotify;

/**
 * StoreWeightLimitNotify.
 *
 * Client reads reliquary limit from wire field 4; writing the wrong field yields n/0
 * and soft-locks the bag UI.
 */
public class PacketStoreWeightLimitNotify extends BasePacket {

    public PacketStoreWeightLimitNotify() {
        super(PacketOpcodes.StoreWeightLimitNotify);

        int relics = Math.max(1, INVENTORY_LIMITS.relics);
        int materials = Math.max(1, INVENTORY_LIMITS.materials);
        int furniture = Math.max(1, INVENTORY_LIMITS.furniture);
        int all = Math.max(1, INVENTORY_LIMITS.all);

        StoreWeightLimitNotify p =
                StoreWeightLimitNotify.newBuilder()
                        .setStoreType(StoreType.StoreType_STORE_PACK)
                        .setReliquaryCountLimit(relics) // wire 4
                        .setWeightLimit(all) // wire 9
                        .setCDHAIJGOKFC(furniture) // wire 14
                        .build();

        this.setData(p);
    }
}
