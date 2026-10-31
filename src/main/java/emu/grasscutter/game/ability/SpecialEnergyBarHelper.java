/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.data.excels.avatar.AvatarSkillData
 *  emu.grasscutter.data.excels.avatar.AvatarSkillDepotData
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.FightProperty
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify
 *  emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketCanUseSkillNotify
 *  emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.data.excels.avatar.AvatarSkillData;
import emu.grasscutter.data.excels.avatar.AvatarSkillDepotData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketCanUseSkillNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import java.util.HashMap;

public final class SpecialEnergyBarHelper {
    private static final float DEFAULT_MAX = 200.0f;
    private static final float DEFAULT_START = 100.0f;
    private static final float SKIRK_BAR_MAX = 100.0f;

    private SpecialEnergyBarHelper() {
    }

    public static void ensureAndSync(GameEntity gameEntity) {
        float f;
        boolean bl;
        float f2;
        Avatar avatar;
        EntityAvatar entityAvatar;
        block27: {
            if (!(gameEntity instanceof EntityAvatar)) {
                return;
            }
            entityAvatar = (EntityAvatar)gameEntity;
            avatar = entityAvatar.getAvatar();
            if (avatar == null) {
                return;
            }
            f2 = 0.0f;
            bl = avatar.getAvatarId() == 10000114;
            try {
                AvatarSkillDepotData avatarSkillDepotData = avatar.getSkillDepot();
                if (avatarSkillDepotData == null || avatarSkillDepotData.getEnergySkillData() == null) {
                    if (!bl) {
                        return;
                    }
                } else {
                    AvatarSkillData avatarSkillData = avatarSkillDepotData.getEnergySkillData();
                    if (avatarSkillData.getSpecialEnergyMin() <= 0.0f && !bl) {
                        return;
                    }
                    f2 = Math.max(avatarSkillData.getSpecialEnergyMin(), avatarSkillData.getSpecialEnergyMax());
                }
            }
            catch (Throwable throwable) {
                if (bl || avatar.getAvatarId() == 10000106) break block27;
                return;
            }
        }
        if (bl) {
            f2 = 100.0f;
        } else if (f2 < 1.0f) {
            f2 = 200.0f;
        }
        float f3 = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY);
        if (f3 < 1.0f || Math.abs(f3 - f2) > 0.5f) {
            f3 = f2;
            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, f3);
        }
        float f4 = avatar.getFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY);
        if (bl) {
            if (f4 > 0.01f) {
                f4 = 0.0f;
                avatar.setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, f4);
            }
        } else if (f4 < 1.0f || f4 > f3 + 0.5f) {
            f4 = Math.min(100.0f, f3);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, f4);
        }
        if ((f = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY)) < 0.0f) {
            f = 0.0f;
        }
        if (f > f3) {
            f = f3;
        }
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY, f3);
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY, f4);
        entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY, f);
        Player player = entityAvatar.getPlayer();
        if (player != null) {
            HashMap<Integer, Float> hashMap = new HashMap<Integer, Float>();
            hashMap.put(FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY.getId(), Float.valueOf(f3));
            hashMap.put(FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY.getId(), Float.valueOf(f4));
            hashMap.put(FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY.getId(), Float.valueOf(f));
            try {
                player.sendPacket((BasePacket)new PacketAvatarFightPropUpdateNotify(avatar, hashMap));
                player.sendPacket((BasePacket)new PacketAvatarFightPropNotify(avatar));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            if (bl && f + 0.5f >= 50.0f) {
                try {
                    player.sendPacket((BasePacket)new PacketCanUseSkillNotify(true));
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
            }
        }
        if (entityAvatar.getScene() != null) {
            try {
                entityAvatar.getScene().broadcastPacket((BasePacket)new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_CUR_SPECIAL_ENERGY));
                entityAvatar.getScene().broadcastPacket((BasePacket)new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_MAX_SPECIAL_ENERGY));
                entityAvatar.getScene().broadcastPacket((BasePacket)new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, FightProperty.FIGHT_PROP_START_SPECIAL_ENERGY));
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }
}

