package emu.grasscutter.game.avatar;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ProudSkillExtraLevelNotifyOuterClass;

/** Client notify for Tartaglia Master of Weaponry (party Normal Attack +1). */
final class TartagliaTeamBonusNotify {
    private TartagliaTeamBonusNotify() {}

    static void send(Player player, Avatar avatar, int talentIndex, int extraLevel) {
        if (player == null || avatar == null) {
            return;
        }
        ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify notify =
                ProudSkillExtraLevelNotifyOuterClass.ProudSkillExtraLevelNotify.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .setTalentType(3)
                        .setTalentIndex(talentIndex)
                        .setExtraLevel(extraLevel)
                        .build();
        BasePacket packet = new BasePacket(PacketOpcodes.ProudSkillExtraLevelNotify);
        packet.setData(notify.toByteArray());
        player.sendPacket(packet);
    }
}
