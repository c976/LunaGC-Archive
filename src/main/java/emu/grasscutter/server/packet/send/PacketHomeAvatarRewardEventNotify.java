package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;

public class PacketHomeAvatarRewardEventNotify extends BasePacket {
    public PacketHomeAvatarRewardEventNotify(Player homeOwner) {
        super(PacketOpcodes.HomeAvatarRewardEventNotify);

        var homeWorld = homeOwner.getCurHomeWorld();
        var moduleManager = homeWorld == null ? null : homeWorld.getModuleManager();
        if (moduleManager != null) {
            this.setData(moduleManager.toRewardEventProto());
        }
    }
}
