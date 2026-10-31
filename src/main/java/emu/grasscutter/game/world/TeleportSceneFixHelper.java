/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  emu.grasscutter.Grasscutter
 *  emu.grasscutter.game.avatar.Avatar
 *  emu.grasscutter.game.entity.EntityAvatar
 *  emu.grasscutter.game.entity.GameEntity
 *  emu.grasscutter.game.player.Player
 *  emu.grasscutter.game.player.TeamManager
 *  emu.grasscutter.game.props.FightProperty
 *  emu.grasscutter.game.world.Scene
 *  emu.grasscutter.net.packet.BasePacket
 *  emu.grasscutter.net.proto.VisionTypeOuterClass$VisionType
 *  emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify
 *  emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify
 */
package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.VisionTypeOuterClass;
import emu.grasscutter.server.packet.send.PacketAvatarFightPropUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarLifeStateChangeNotify;

public final class TeleportSceneFixHelper {
    private TeleportSceneFixHelper() {
    }

    public static void fixSpawn(Player player) {
        if (player == null) {
            return;
        }
        Scene scene = player.getScene();
        if (scene == null) {
            return;
        }
        TeamManager teamManager = player.getTeamManager();
        if (teamManager == null) {
            return;
        }
        EntityAvatar entityAvatar = teamManager.getCurrentAvatarEntity();
        if (entityAvatar == null) {
            scene.spawnPlayer(player);
            return;
        }
        try {
            if (player.getPosition() != null) {
                entityAvatar.getPosition().set(player.getPosition());
            }
            if (player.getRotation() != null) {
                entityAvatar.getRotation().set(player.getRotation());
            }
            float f = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            float f2 = entityAvatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
            if (f2 > 1.0f && (f <= 0.0f || f < f2 * 0.05f)) {
                entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, f2);
                player.sendPacket((BasePacket)new PacketAvatarFightPropUpdateNotify(entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                player.sendPacket((BasePacket)new PacketAvatarLifeStateChangeNotify(entityAvatar.getAvatar()));
            } else if (f <= 0.0f) {
                entityAvatar.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1.0f);
                player.sendPacket((BasePacket)new PacketAvatarFightPropUpdateNotify(entityAvatar.getAvatar(), FightProperty.FIGHT_PROP_CUR_HP));
                player.sendPacket((BasePacket)new PacketAvatarLifeStateChangeNotify(entityAvatar.getAvatar()));
            }
            if (scene.isInScene((GameEntity)entityAvatar)) {
                scene.removeEntity((GameEntity)entityAvatar, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
            }
            scene.addEntity((GameEntity)entityAvatar);
            teamManager.getActiveTeam().stream().map(EntityAvatar::getAvatar).forEach(Avatar::sendSkillExtraChargeMap);
        }
        catch (Throwable throwable) {
            Grasscutter.getLogger().warn("TeleportSceneFixHelper failed, fallback spawnPlayer: {}", (Object)throwable.toString());
            try {
                scene.spawnPlayer(player);
            }
            catch (Throwable throwable2) {
                // empty catch block
            }
        }
    }
}

