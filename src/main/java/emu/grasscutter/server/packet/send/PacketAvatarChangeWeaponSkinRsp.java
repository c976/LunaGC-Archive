package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarChangeWeaponSkinRsp._AvatarChangeWeaponSkinRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass;

import java.util.List;

/** _AvatarChangeWeaponSkinRsp (7.1 cmd 575). */
public class PacketAvatarChangeWeaponSkinRsp extends BasePacket {

    public PacketAvatarChangeWeaponSkinRsp(List<Long> avatarGuids, int weaponSkinId) {
        super(PacketOpcodes._AvatarChangeWeaponSkinRsp);

        _AvatarChangeWeaponSkinRsp.Builder proto =
                _AvatarChangeWeaponSkinRsp.newBuilder().setWeaponSkinId(weaponSkinId);
        if (avatarGuids != null) {
            proto.addAllAvatarGuidList(avatarGuids);
        }

        this.setData(proto);
    }

    public PacketAvatarChangeWeaponSkinRsp() {
        super(PacketOpcodes._AvatarChangeWeaponSkinRsp);

        _AvatarChangeWeaponSkinRsp proto =
                _AvatarChangeWeaponSkinRsp.newBuilder()
                        .setRetcode(RetcodeOuterClass.Retcode.RET_SVR_ERROR_VALUE)
                        .build();

        this.setData(proto);
    }
}
