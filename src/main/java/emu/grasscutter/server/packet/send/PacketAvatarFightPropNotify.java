package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarFightPropNotifyOuterClass.AvatarFightPropNotify;
import java.util.HashMap;

public class PacketAvatarFightPropNotify extends BasePacket {

    private static final int PROP_HP_DEBTS = FightProperty.FIGHT_PROP_CUR_HP_DEBTS.getId();
    private static final int ARLECCHINO_AVATAR_ID = 10000096;

    public PacketAvatarFightPropNotify(Avatar avatar) {
        super(PacketOpcodes.AvatarFightPropNotify);

        var props = new HashMap<>(avatar.getFightProperties());
        // A full Notify normally omits a prop to mean "unchanged", but the client reads a missing
        // HP_DEBTS as 0 - so any full Notify sent with no Bond change (avatar upgrade on kill,
        // MaxHP recompute, etc.) wiped Arlecchino's Bond of Life bar until the next entity re-sync
        // (character switch / teleport). Always echo the live entity value for her instead.
        EntityAvatar debtEntity = findArlecchinoEntity(avatar);
        if (debtEntity != null) {
            props.put(
                    PROP_HP_DEBTS,
                    debtEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        } else {
            props.remove(PROP_HP_DEBTS);
        }

        AvatarFightPropNotify proto =
                AvatarFightPropNotify.newBuilder()
                        .setAvatarGuid(avatar.getGuid())
                        .putAllFightPropMap(props)
                        .build();

        this.setData(proto);
    }

    /** The on-field Arlecchino entity for this avatar, whose fight props hold the live Bond. */
    private static EntityAvatar findArlecchinoEntity(Avatar avatar) {
        if (avatar == null || avatar.getAvatarId() != ARLECCHINO_AVATAR_ID) {
            return null;
        }
        try {
            var player = avatar.getPlayer();
            if (player == null || player.getTeamManager() == null) {
                return null;
            }
            for (EntityAvatar ent : player.getTeamManager().getActiveTeam()) {
                if (ent != null && ent.getAvatar() == avatar) {
                    return ent;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
