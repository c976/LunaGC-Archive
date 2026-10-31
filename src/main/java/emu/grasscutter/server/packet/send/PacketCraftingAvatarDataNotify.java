package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

/**
 * Initializes the additional crafting avatar lists required by SynthesisPage.
 *
 * <p>Command 21136 is missing from the 7.1 descriptors. An empty protobuf payload initializes both
 * repeated fields (1 and 15), allowing page setup to reach button binding.
 */
public final class PacketCraftingAvatarDataNotify extends BasePacket {
    public PacketCraftingAvatarDataNotify() {
        super(PacketOpcodes.CraftingAvatarDataNotify);
        setData(new byte[0]);
    }
}
