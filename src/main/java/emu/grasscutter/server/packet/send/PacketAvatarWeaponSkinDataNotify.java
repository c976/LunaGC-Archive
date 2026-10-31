package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarWeaponSkinDataNotify._AvatarWeaponSkinDataNotify;
import emu.grasscutter.net.proto.AvatarWeaponSkinDataNotify._WeaponSkinInfo;

import java.util.TreeSet;

/**
 * _AvatarWeaponSkinDataNotify (7.1 cmd 29904): unlocks + equipped-avatar mapping for every weapon
 * skin the player owns. Field numbers recovered from the 7.1 obfuscated dump.
 */
public class PacketAvatarWeaponSkinDataNotify extends BasePacket {

    /**
     * Far future in BOTH seconds (y8,000,000) and milliseconds (y9999). Truncated to
     * uint32 it still lands in 2081, so every plausible client-side interpretation
     * reads as "never expires".
     */
    private static final long PERMANENT_EXPIRE = 253402300799000L;

    public PacketAvatarWeaponSkinDataNotify(Player player) {
        super(PacketOpcodes._AvatarWeaponSkinDataNotify);

        _AvatarWeaponSkinDataNotify.Builder notify = _AvatarWeaponSkinDataNotify.newBuilder();

        var avatars = player.getAvatars().getAvatars().values();
        for (int skinId : new TreeSet<>(player.getWeaponSkinList())) {
            notify.addRecordIdList(skinId);

            var info =
                    _WeaponSkinInfo.newBuilder()
                            .setWeaponSkinId(skinId)
                            .setExpireTime(PERMANENT_EXPIRE);
            for (var avatar : avatars) {
                if (avatar.getWeaponSkinId() == skinId) {
                    info.addEquippedAvatarGuidList(avatar.getGuid());
                }
            }
            notify.addInfoList(info);
        }

        notify.setTimestamp(System.currentTimeMillis() / 1000L);
        this.setData(notify.build());
    }
}
