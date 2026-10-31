package emu.grasscutter;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.utils.Utils;
import emu.grasscutter.utils.objects.SparseSet;

import java.util.Arrays;

public final class GameConstants {
    public static String VERSION = "7.1.0";
    public static int[] VERSION_PARTS = Arrays.stream(VERSION.split("\\.")).mapToInt(Integer::parseInt).toArray();
    public static boolean DEBUG = false;

    public static final int ENTITY_ID_BIT_SHIFT = 21;
    public static final int DEFAULT_TEAMS = 4;
    // 20 party slots in total (the 4 defaults plus 16 custom ones), matching the official client.
    public static final int MAX_TEAMS = 20;
    public static final int MAIN_CHARACTER_MALE = 10000005;
    public static final int MAIN_CHARACTER_FEMALE = 10000007;
    public static final Position START_POSITION = new Position(2747, 194, -1719);
    public static final int MAX_FRIENDS = 60;
    public static final int MAX_FRIEND_REQUESTS = 50;
    public static final int SERVER_CONSOLE_UID = 99;
    /** DPS commander friend. */
    public static final int SERVER_DPS_UID = 97;

    public static boolean isServerBotUid(int uid) {
        return uid == SERVER_CONSOLE_UID || uid == SERVER_DPS_UID;
    }
    public static final int BATTLE_PASS_MAX_LEVEL = 50;
    public static final int BATTLE_PASS_POINT_PER_LEVEL = 1000;
    public static final int BATTLE_PASS_POINT_PER_WEEK = 10000;
    public static final int BATTLE_PASS_LEVEL_PRICE = 150;
    public static final int BATTLE_PASS_CURRENT_INDEX = 2;

    /**
     * Avatar default embryos. Keep aligned with ConfigGlobalCombat.defaultAvatarAbilities where
     * possible.
     *
     * Do NOT put Dive_Team / Absorb / SealEcho / CrabShield / DiveStamina / FluidAgitator /
     * DiveVolume here. Official defaults omit them; putting them in defaults rebuilds for every
     * avatar on login/team swap/death/revive and plays the Fontaine blue water circle on land for
     * anyone. Attach only while diving via {@link emu.grasscutter.game.player.DiveAbilityHelper}.
     */
    public static final String DIVE_TEAM_ABILITY = "Ability_Avatar_Dive_Team";

    /** Avatar-side dive combat — dynamic only (see DiveAbilityHelper). */
    public static final String[] DIVE_AVATAR_ABILITIES = {
        "Avatar_PlayerBoy_DiveStamina_Reduction",
        "Ability_Avatar_Dive_SealEcho",
        "Absorb_SealEcho_Bullet_01",
        "Absorb_SealEcho_Bullet_02",
        "Ability_Avatar_Dive_CrabShield",
        "ActivityAbility_Absorb_Shoot",
        "Avatar_PlayerGirl_DiveStamina_Reduction",
        "Avatar_Absorb_TrackingMissile",
        "Avatar_Absorb_SwordFishSlash"
    };

    public static final String[] DEFAULT_ABILITY_STRINGS = {
        "Avatar_DefaultAbility_VisionReplaceDieInvincible",
         "Avatar_DefaultAbility_AvartarInShaderChange",
         "Avatar_SprintBS_Invincible",
         "Avatar_Freeze_Duration_Reducer",
         "Avatar_Attack_ReviveEnergy",
         "Avatar_Component_Initializer",
         "Avatar_FallAnthem_Achievement_Listener",
         "GrapplingHookSkill_Ability",
         "TeamAbility_Natsaurus_Transfer_Vehicle_Skill",
         "DynamicAbility_Phlogiston",
         "DynamicAbility_NightsoulBlessing",
         "TeamAbility_Natsaurus_Vehicle_State_Listener",
         "DynamicAbility_ArcLight_Predicate",
         "DynamicAbility_CommonArcLight_Invincible_V5_0",
         "DynamicAbility_ArcLight_Wathcer",
         "SceneObj_Area_Nt_Property_Prop_YouLieDragon_Collision_DynamicAbility",
         "TeamAbility_Natsaurus_Preload_Drillhead",
         "TeamAbility_Natsaurus_Preload_Hookwalker",
         "TeamAbility_Natsaurus_Preload_Mosasaurus",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet",
         "TeamAbility_NightsoulBurst",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_01",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_02",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_03",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_04",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_05",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_06",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_07",
         "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_5001",
         "Avatar_HDMesh_Controller",
         "Avatar_Trampoline_Jump_Controller",
         "Avatar_ArkheGrade_CD_Controller",
         "Avatar_TriggerNyxInstant",
         "Avatar_NyxState_Listener",
         "Avatar_StarSuperconductor_Field_Checker"
     };
      public static final String[] DEFAULT_TEAM_ABILITY_STRINGS = {
             "TeamAbility_MoonPhase",

             "TeamAbility_Natsaurus_Transfer_Vehicle_Skill",
             "DynamicAbility_Phlogiston",
             "TeamAbility_Natsaurus_Vehicle_State_Listener",
             "DynamicAbility_ArcLight_Predicate",
             "DynamicAbility_CommonArcLight_Invincible_V5_0",
             "DynamicAbility_ArcLight_Wathcer",
             "SceneObj_Area_Nt_Property_Prop_YouLieDragon_Collision_DynamicAbility",
             "TeamAbility_Natsaurus_Preload_Drillhead",
             "TeamAbility_Natsaurus_Preload_Hookwalker",
             "TeamAbility_Natsaurus_Preload_Mosasaurus",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_01",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_02",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_03",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_04",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_05",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_06",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_07",
             "TeamAbility_Natsaurus_Hookwalker_ElementalArt_TriggerBullet_5001",
             "TeamAbility_NightsoulBurst",
             "TeamAbility_Reset_Crystal_Mark",
             "TeamAbility_Reset_MoonOvergrow",
             "TeamAbility_MoonCrystal_Water",
             "TeamAbility_StarSuperconductor",
             "TeamAbility_StarSwirl_Ice"
     };
    public static final SparseSet ILLEGAL_WEAPONS = new SparseSet("""
        10000-10008, 11411, 11506-11508, 12505, 12506, 12508, 12509,
        13503, 13506, 14411, 14503, 14505, 14508, 15504-15506
        """);
    public static final SparseSet ILLEGAL_RELICS = new SparseSet("""
        20001, 23300-23340, 23383-23385, 78310-78554, 99310-99554
        """);
    public static final SparseSet ILLEGAL_ITEMS = new SparseSet("""
        100086, 100087, 100100-101000, 101106-101110, 101306, 101500-104000,
        105001, 105004, 106000-107000, 107011, 108000, 109000-110000,
        115000-130000, 200200-200899, 220050, 220054
        """);
    public static final int[] DEFAULT_ABILITY_HASHES =
            Arrays.stream(DEFAULT_ABILITY_STRINGS).mapToInt(Utils::abilityHash).toArray();
    private static final int PHLOGISTON_ABILITY_HASH = Utils.abilityHash("DynamicAbility_Phlogiston");

    /**
     * The avatar AbilityControlBlock and server-side instance list must use identical filtering:
     * the client addresses abilities by the dense, scene-specific ability ID.
     */
    public static boolean shouldAttachDefaultAvatarAbility(int abilityHash, boolean inNatlan) {
        return inNatlan || abilityHash != PHLOGISTON_ABILITY_HASH;
    }

    public static final int DEFAULT_ABILITY_NAME = Utils.abilityHash("Default");
}
