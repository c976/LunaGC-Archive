package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.dungeons.DomainDungeonHelper;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.tps.TpsWeaponSystem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.SceneTeamAvatarOuterClass.SceneTeamAvatar;
import emu.grasscutter.net.proto.SceneTeamUpdateNotifyOuterClass.SceneTeamUpdateNotify;

public class PacketSceneTeamUpdateNotify extends BasePacket {
    public PacketSceneTeamUpdateNotify(Player player) {
        super(PacketOpcodes.SceneTeamUpdateNotify);

        var proto = SceneTeamUpdateNotify.newBuilder().setIsInMp(player.getWorld().isMultiplayer());

        for (var p : player.getWorld().getPlayers()) {
            for (var entityAvatar : p.getTeamManager().getActiveTeam(true)) {
                // Build each avatar on its own. An avatar with incomplete data (e.g. an unreleased
                // character whose skill depot or abilities are not in this resource set) used to
                // throw here and abort the whole loop, so the packet went out without any team
                // member's AbilityControlBlock - which left every teammate unable to cast E or Q.
                // Now a bad avatar is skipped and the rest of the team is unaffected.
                try {
                    var avatarProto =
                            SceneTeamAvatar.newBuilder()
                                    .setPlayerUid(p.getUid())
                                    .setAvatarGuid(entityAvatar.getAvatar().getGuid())
                                    .setSceneId(DomainDungeonHelper.notifySceneId(p))
                                    .setEntityId(entityAvatar.getId())
                                    .setSceneEntityInfo(entityAvatar.toProto())
                                    .setWeaponGuid(entityAvatar.getAvatar().getWeaponNotNull().getGuid())
                                    .setWeaponEntityId(entityAvatar.getWeaponEntityId())
                                    .setIsPlayerCurAvatar(
                                            p.getTeamManager().getCurrentAvatarEntity() == entityAvatar)
                                    .setIsOnScene(
                                            p.getTeamManager().getCurrentAvatarEntity() == entityAvatar)
                                    .setAvatarAbilityInfo(AbilitySyncStateInfo.newBuilder())
                                    .setWeaponAbilityInfo(AbilitySyncStateInfo.newBuilder())
                                    .setAbilityControlBlock(entityAvatar.getAbilityControlBlock())
                                    .addAllTpsWeaponList(
                                            TpsWeaponSystem.getSceneWeaponInfos(entityAvatar.getAvatar()));

                    if (player.getWorld().isMultiplayer()) {
                        avatarProto.setAvatarInfo(entityAvatar.getAvatar().toProto());
                        avatarProto.setSceneAvatarInfo(entityAvatar.getSceneAvatarInfo()); // why mihoyo...
                    }

                    proto.addSceneTeamAvatarList(avatarProto);
                } catch (Throwable e) {
                    Grasscutter.getLogger()
                            .warn(
                                    "Skipping avatar {} in SceneTeamUpdate for uid {}; its data is"
                                            + " incomplete on this server.",
                                    entityAvatar.getAvatar().getAvatarId(),
                                    p.getUid(),
                                    e);
                }
            }
        }

        this.setData(proto);
    }
}
