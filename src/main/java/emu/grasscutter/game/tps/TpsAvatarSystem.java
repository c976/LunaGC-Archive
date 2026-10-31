package emu.grasscutter.game.tps;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.trial.TrialAvatarData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonTrialTeam;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.proto.GrantReasonOuterClass.GrantReason;
import java.util.*;
import javax.annotation.Nullable;

/**
 * The dedicated TPS traveler. The TPS dungeons (Emerged Grey Field, the shooting range) list
 * avatars 10000134/10000135 as their only allowed avatars ({@code SceneExcelConfigData
 * .specifiedAvatarList}); the player enters as the one matching their traveler, a level 20 trial
 * avatar (TrialAvatarExcelConfigData 10064/10065), wearing the TPS weapons they picked last time.
 */
public final class TpsAvatarSystem {
    /** CONST_VALUE_TPS_AVATAR_CONFIG_ID_MALE. */
    public static final int TPS_AVATAR_MALE = 10000134;

    /** CONST_VALUE_TPS_AVATAR_CONFIG_ID_FEMALE. */
    public static final int TPS_AVATAR_FEMALE = 10000135;

    /** CONST_VALUE_INIT_TPS_WEAPON_ID: what the TPS traveler carries before any choice is made. */
    public static final int INIT_TPS_WEAPON_ID = 224001;

    private static final int TRAVELER_FEMALE = 10000007;

    private TpsAvatarSystem() {}

    public static boolean isTpsAvatar(int avatarId) {
        return avatarId == TPS_AVATAR_MALE || avatarId == TPS_AVATAR_FEMALE;
    }

    public static boolean isTpsAvatar(@Nullable Avatar avatar) {
        return avatar != null && isTpsAvatar(avatar.getAvatarId());
    }

    public static int getTpsAvatarId(Player player) {
        return player.getMainCharacterId() == TRAVELER_FEMALE ? TPS_AVATAR_FEMALE : TPS_AVATAR_MALE;
    }

    public static boolean isTpsScene(@Nullable SceneData sceneData) {
        if (sceneData == null || sceneData.getSpecifiedAvatarList() == null) return false;
        return sceneData.getSpecifiedAvatarList().stream().anyMatch(TpsAvatarSystem::isTpsAvatar);
    }

    /** The trial avatar entry for the player's TPS traveler; the lowest id wins. */
    public static int getTrialAvatarId(Player player) {
        int avatarId = getTpsAvatarId(player);
        return GameData.getTrialAvatarDataMap().values().stream()
                .filter(data -> data.getTrialAvatarParamList() != null)
                .filter(data -> !data.getTrialAvatarParamList().isEmpty())
                .filter(data -> data.getTrialAvatarParamList().get(0) == avatarId)
                .mapToInt(TrialAvatarData::getTrialAvatarId)
                .filter(id -> !player.getTeamManager().getTrialAvatarParam(id).isEmpty())
                .min()
                .orElse(0);
    }

    /** The team a TPS scene puts the player in, or null when the scene is not a TPS scene. */
    @Nullable public static DungeonTrialTeam getTrialTeam(Player player, @Nullable Scene scene) {
        if (scene == null || !isTpsScene(scene.getSceneData())) return null;
        int trialAvatarId = getTrialAvatarId(player);
        if (trialAvatarId == 0) return null;
        return new DungeonTrialTeam(
                new ArrayList<>(List.of(trialAvatarId)), GrantReason.GRANT_REASON_BY_TRIAL_AVATAR_ACTIVITY);
    }

    /**
     * Dresses a freshly made TPS traveler trial avatar in the player's TPS loadout, handing out the
     * initial weapon when there is none yet. Called before its stats are first calculated.
     */
    public static void onTrialAvatarCreated(Avatar avatar) {
        if (!isTpsAvatar(avatar) || avatar.getPlayer() == null) return;
        var player = avatar.getPlayer();
        var loadout = player.getTpsLoadout();

        loadout.removeIf(itemId -> TpsWeaponSystem.findOwnedWeapon(player, itemId) == null);
        if (loadout.isEmpty()) {
            if (TpsWeaponSystem.findOwnedWeapon(player, INIT_TPS_WEAPON_ID) == null) {
                player.getInventory().addItem(INIT_TPS_WEAPON_ID);
            }
            if (TpsWeaponSystem.findOwnedWeapon(player, INIT_TPS_WEAPON_ID) != null) {
                loadout.add(INIT_TPS_WEAPON_ID);
            }
        }

        avatar.getTpsWeaponIds().clear();
        avatar.getTpsWeaponIds().addAll(loadout);
    }
}
