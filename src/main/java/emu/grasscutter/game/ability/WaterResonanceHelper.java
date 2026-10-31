/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.props.ElementType
 *  emu.grasscutter.game.props.FightProperty
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify
 *  emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify
 *  it.unimi.dsi.fastutil.ints.IntSet
 */
package emu.grasscutter.game.ability;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ElementType;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropNotify;
import emu.grasscutter.server.packet.send.PacketEntityFightPropUpdateNotify;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;

public final class WaterResonanceHelper {
    private static final float HP_BONUS = 0.25f;
    private static final ThreadLocal<Boolean> IN_APPLY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private WaterResonanceHelper() {
    }

    public static boolean hasWaterResonance(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return false;
        }
        IntSet intSet = player.getTeamManager().getTeamResonances();
        return intSet != null && intSet.contains(ElementType.Water.getTeamResonanceId());
    }

    public static void refresh(Player player) {
        if (player == null || player.getTeamManager() == null) {
            return;
        }
        try {
            for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
                if (entityAvatar == null || entityAvatar.getAvatar() == null) continue;
                entityAvatar.getAvatar().recalcStats(true);
            }
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("WaterResonance refresh failed: {}", (Object)throwable.toString());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void afterRecalc(Avatar avatar) {
        if (avatar == null || Boolean.TRUE.equals(IN_APPLY.get())) {
            return;
        }
        Player player = avatar.getPlayer();
        if (player == null || !WaterResonanceHelper.hasWaterResonance(player)) {
            return;
        }
        if (!WaterResonanceHelper.isOnActiveTeam(player, avatar)) {
            return;
        }
        IN_APPLY.set(Boolean.TRUE);
        try {
            float f;
            float f2 = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            float f3 = avatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            if (f2 <= 1.0f || Float.isNaN(f2)) {
                return;
            }
            float f4 = f2 * 1.25f;
            float f5 = f = f3 > 0.0f ? f3 / f2 : 1.0f;
            if (f > 1.0f) {
                f = 1.0f;
            }
            float f6 = f4 * f;
            avatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, f4);
            avatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f6);
            EntityAvatar entityAvatar = WaterResonanceHelper.findEntity(player, avatar);
            if (entityAvatar != null) {
                entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, f4);
                entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f6);
            }
            player.sendPacket((BasePacket)new PacketAvatarFightPropNotify(avatar));
            if (entityAvatar != null) {
                List<FightProperty> list = Arrays.asList(FightProperty.FIGHT_PROP_MAX_HP, FightProperty.FIGHT_PROP_CUR_HP);
                PacketEntityFightPropUpdateNotify packetEntityFightPropUpdateNotify = new PacketEntityFightPropUpdateNotify((GameEntity)entityAvatar, list);
                player.sendPacket((BasePacket)packetEntityFightPropUpdateNotify);
                if (entityAvatar.getScene() != null) {
                    entityAvatar.getScene().broadcastPacket((BasePacket)packetEntityFightPropUpdateNotify);
                }
            }
            Grasscutter.getLogger().info("Water resonance HP +25% avatarId={} {} -> {} (uid={})", new Object[]{avatar.getAvatarId(), Float.valueOf(f2), Float.valueOf(f4), player.getUid()});
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("WaterResonance afterRecalc failed: {}", (Object)throwable.toString());
        }
        finally {
            IN_APPLY.set(Boolean.FALSE);
        }
    }

    private static boolean isOnActiveTeam(Player player, Avatar avatar) {
        for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
            if (entityAvatar == null || entityAvatar.getAvatar() == null || entityAvatar.getAvatar().getGuid() != avatar.getGuid()) continue;
            return true;
        }
        return false;
    }

    private static EntityAvatar findEntity(Player player, Avatar avatar) {
        for (EntityAvatar entityAvatar : player.getTeamManager().getActiveTeam()) {
            if (entityAvatar == null || entityAvatar.getAvatar() == null || entityAvatar.getAvatar().getGuid() != avatar.getGuid()) continue;
            return entityAvatar;
        }
        return null;
    }
}

