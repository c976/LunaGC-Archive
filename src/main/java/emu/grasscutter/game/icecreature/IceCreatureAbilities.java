package emu.grasscutter.game.icecreature;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.entity.EntityAvatar;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.SnezhnayaFrigid;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.utils.Utils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The avatar half of the Snezhnaya area abilities: {@code DynamicAbility_IceCreature_V7_0_Avatar},
 * which gives every character the Icemaking create/destroy/select skills and the ride and launcher
 * controls the creatures use.
 *
 * <p>The team half rides on {@link SnezhnayaFrigid}'s grant. These are appended to each avatar's
 * control block while that grant holds, after every other embryo, the same way dive abilities are.
 */
public final class IceCreatureAbilities {
    public static final List<String> AVATAR_ABILITIES =
            List.of(
                    "DynamicAbility_IceCreature_Create_Skill",
                    "DynamicAbility_IceCreature_Destory_Skill",
                    "DynamicAbility_IceCreature_Selection_Skill",
                    "DynamicAbility_IceCreature_System_Skill",
                    "DynamicAbility_Cannon_Quit",
                    "DynamicAbility_SnowBall_Shoot",
                    "DynamicAbility_Zipline_AvatarAbility",
                    "DynamicAbility_Zipline_AvatarAbility_Exit",
                    "DynamicAbility_IceCreature_JumpBoard_Jump_Controller");

    private IceCreatureAbilities() {}

    /** Appends the Icemaking embryos while the player holds the Snezhnaya grant. */
    public static int appendEmbryos(EntityAvatar avatar, AbilityControlBlock.Builder block, int embryoId) {
        if (avatar == null || block == null || !SnezhnayaFrigid.isGranted(avatar.getPlayer())) return embryoId;
        Set<Integer> existing = new HashSet<>();
        for (AbilityEmbryo embryo : block.getAbilityEmbryoListList()) existing.add(embryo.getAbilityNameHash());
        for (String name : AVATAR_ABILITIES) {
            int hash = Utils.abilityHash(name);
            if (!existing.add(hash)) continue;
            block.addAbilityEmbryoList(
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++embryoId)
                            .setAbilityNameHash(hash)
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build());
        }
        return embryoId;
    }

    /** Server-side instances for a freshly initialized avatar, in control block order. */
    public static void addInstances(Player player, EntityAvatar avatar) {
        if (!SnezhnayaFrigid.isGranted(player)) return;
        for (String name : AVATAR_ABILITIES) addInstance(player, avatar, name);
    }

    /**
     * Follows a grant or withdrawal for the active team.
     *
     * @param notify whether to resend each control block; false while entering a scene, whose packets
     *     carry the blocks anyway
     */
    public static void sync(Player player, boolean enable, boolean notify) {
        if (player.getTeamManager() == null) return;
        for (EntityAvatar avatar : player.getTeamManager().getActiveTeam()) {
            if (avatar == null) continue;
            var instances = avatar.getInstancedAbilities();
            // An avatar that has not initialized yet builds the list from the control block later.
            if (!instances.isEmpty()) {
                if (enable) {
                    for (String name : AVATAR_ABILITIES) addInstance(player, avatar, name);
                } else {
                    instances.removeIf(
                            a -> a != null && a.getData() != null && AVATAR_ABILITIES.contains(a.getData().abilityName));
                }
            }
            if (notify) player.sendPacket(new PacketAbilityChangeNotify(avatar));
        }
    }

    private static void addInstance(Player player, EntityAvatar avatar, String name) {
        var data = GameData.getAbilityData(name);
        if (data == null) return;
        boolean present =
                avatar.getInstancedAbilities().stream()
                        .anyMatch(a -> a != null && a.getData() != null && name.equals(a.getData().abilityName));
        if (!present) player.getAbilityManager().addAbilityToEntity(avatar, data);
    }
}
