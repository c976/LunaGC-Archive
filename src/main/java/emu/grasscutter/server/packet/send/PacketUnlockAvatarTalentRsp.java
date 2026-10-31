package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.UnlockAvatarTalentRspOuterClass.UnlockAvatarTalentRsp;

public class PacketUnlockAvatarTalentRsp extends BasePacket {

    public PacketUnlockAvatarTalentRsp(Avatar avatar, int talentId) {
        this(avatar.getGuid(), talentId, Retcode.RET_SUCC);
    }

    public PacketUnlockAvatarTalentRsp(long avatarGuid, int talentId, Retcode retcode) {
        super(PacketOpcodes.UnlockAvatarTalentRsp);

        UnlockAvatarTalentRsp proto =
                UnlockAvatarTalentRsp.newBuilder()
                        .setAvatarGuid(avatarGuid)
                        .setTalentId(talentId)
                        .setRetcode(retcode.getNumber())
                        .build();

        this.setData(proto);
    }
}
