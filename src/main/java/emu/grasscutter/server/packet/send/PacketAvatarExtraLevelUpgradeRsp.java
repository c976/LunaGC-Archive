package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.AvatarExtraLevelOpcodes;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AvatarExtraLevelUpgradeRsp._AvatarExtraLevelUpgradeRsp;

public class PacketAvatarExtraLevelUpgradeRsp extends BasePacket {
    public PacketAvatarExtraLevelUpgradeRsp(long avatarGuid, int oldLevel, int curLevel) {
        this(avatarGuid, oldLevel, curLevel, 0);
    }

    public PacketAvatarExtraLevelUpgradeRsp(long avatarGuid, int oldLevel, int curLevel, int retcode) {
        super(AvatarExtraLevelOpcodes.resolveResponseOpcode(0));
        this.setData(
                _AvatarExtraLevelUpgradeRsp.newBuilder()
                        .setAvatarGuid(avatarGuid)
                        .setOldLevel(oldLevel)
                        .setCurLevel(curLevel)
                        .setRetcode(retcode)
                        .build());
    }
}
