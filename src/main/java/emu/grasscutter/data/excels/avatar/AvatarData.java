package emu.grasscutter.data.excels.avatar;

import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.common.PropGrowCurve;
import emu.grasscutter.data.excels.FetterCharacterCardData;
import emu.grasscutter.game.props.*;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import lombok.Getter;

import java.util.*;

@ResourceType(name = "AvatarExcelConfigData.json", loadPriority = LoadPriority.LOW)
public class AvatarData extends GameResource {

    @Getter private String iconName;
    @Getter private String bodyType;
    @Getter private String useType;
    @Getter private String qualityType;
    @Getter private int chargeEfficiency;
    @Getter private int initialWeapon;
    @Getter private WeaponType weaponType;
    @Getter private String imageName;
    @Getter private int avatarPromoteId;
    @Getter private String cutsceneShow;
    @Getter private int skillDepotId;
    @Getter private int staminaRecoverSpeed;
    @Getter private List<Integer> candSkillDepotIds;
    @Getter private String avatarIdentityType;
    @Getter private List<Integer> avatarPromoteRewardLevelList;
    @Getter private List<Integer> avatarPromoteRewardIdList;
    @Getter private List<String> tags;

    @Getter
    private long nameTextMapHash;

    private float hpBase;
    private float attackBase;
    private float defenseBase;
    private float critical;
    private float criticalHurt;
    /** Flat base EM (Lauma 200 / Nefer 100); most avatars leave this at 0. */
    @Getter private float elementMastery;

    private List<PropGrowCurve> propGrowCurves;
    @Getter(onMethod_ = @Override)
    private int id;

    // Transient
    @Getter
    private String name;

    private Int2ObjectMap<String> growthCurveMap;
    private float[] hpGrowthCurve;
    private float[] attackGrowthCurve;
    private float[] defenseGrowthCurve;
    @Getter
    private AvatarSkillDepotData skillDepot;
    @Getter
    private IntList abilities;
    @Getter
    private List<String> abilitieNames = new ArrayList<>();

    @Getter
    private List<Integer> fetters;
    @Getter
    private int nameCardRewardId;
    @Getter
    private int nameCardId;
    /** Fetter level to reward id: the level 10 name card, plus any earlier reward. */
    @Getter
    private transient Int2IntMap fetterRewards = Int2IntMaps.EMPTY_MAP;

    public float getBaseHp(int level) {
        try {
            return this.hpBase * this.hpGrowthCurve[level - 1];
        } catch (Exception e) {
            return this.hpBase;
        }
    }

    public float getBaseAttack(int level) {
        try {
            return this.attackBase * this.attackGrowthCurve[level - 1];
        } catch (Exception e) {
            return this.attackBase;
        }
    }

    public float getBaseDefense(int level) {
        try {
            return this.defenseBase * this.defenseGrowthCurve[level - 1];
        } catch (Exception e) {
            return this.defenseBase;
        }
    }

    public float getBaseCritical() {
        return this.critical;
    }

    public float getBaseCriticalHurt() {
        return this.criticalHurt;
    }

    public float getGrowthCurveById(int level, FightProperty prop) {
        String growCurve = this.growthCurveMap.get(prop.getId());
        if (growCurve == null) {
            return 1f;
        }
        AvatarCurveData curveData = GameData.getAvatarCurveDataMap().get(level);
        if (curveData == null) {
            return 1f;
        }
        return curveData.getCurveInfos().getOrDefault(growCurve, 1f);
    }

    @Override
    public void onLoad() {
        this.skillDepot = GameData.getAvatarSkillDepotDataMap().get(this.skillDepotId);

        // Get fetters from GameData
        this.fetters = GameData.getFetterDataEntries().get(this.id);

        this.fetterRewards = FetterCharacterCardData.rewardsOf(this.id);
        this.nameCardRewardId = this.fetterRewards.get(10);

        if (GameData.getRewardDataMap().get(this.nameCardRewardId) != null) {
            this.nameCardId = GameData.getRewardDataMap().get(this.nameCardRewardId).getRewardItemList().get(0).getItemId();
        }

        int size = GameData.getAvatarCurveDataMap().size();
        this.hpGrowthCurve = new float[size];
        this.attackGrowthCurve = new float[size];
        this.defenseGrowthCurve = new float[size];
        for (AvatarCurveData curveData : GameData.getAvatarCurveDataMap().values()) {
            int level = curveData.getLevel() - 1;
            for (PropGrowCurve growCurve : this.propGrowCurves) {
                FightProperty prop = FightProperty.getPropByName(growCurve.getType());
                switch (prop) {
                    case FIGHT_PROP_BASE_HP:
                        this.hpGrowthCurve[level] = curveData.getCurveInfos().get(growCurve.getGrowCurve());
                        break;
                    case FIGHT_PROP_BASE_ATTACK:
                        this.attackGrowthCurve[level] = curveData.getCurveInfos().get(growCurve.getGrowCurve());
                        break;
                    case FIGHT_PROP_BASE_DEFENSE:
                        this.defenseGrowthCurve[level] = curveData.getCurveInfos().get(growCurve.getGrowCurve());
                        break;
                    default:
                        break;
                }
            }
        }

        // Left commented out for a long while against a field name that no longer exists, so the map
        // stayed null and getGrowthCurveById would have thrown at whoever called it first.
        this.growthCurveMap = new Int2ObjectOpenHashMap<>();
        for (PropGrowCurve growCurve : this.propGrowCurves) {
            FightProperty prop = FightProperty.getPropByName(growCurve.getType());
            if (prop != null) this.growthCurveMap.put(prop.getId(), growCurve.getGrowCurve());
        }

        // Cache abilities
        this.buildEmbryo();
    }

    private static final int TPS_TRAVELER_MALE = 10000134;
    private static final int TPS_TRAVELER_FEMALE = 10000135;

    /** ConfigAvatar_AetherShadow/LumineShadow abilities. */
    private static final List<String> TPS_TRAVELER_ABILITIES =
            List.of(
                    "Avatar_TPS_Innate_Ability",
                    "Avatar_TPS_Animator_Montage_Manager",
                    "Avatar_TPS_Achievement_Listener",
                    "Avatar_TPS_HandGrenade_ThrowBullet",
                    "Avatar_TPS_Grappling_Hook");

    /**
     * Create ability embryos.
     */
    public void buildEmbryo() {
        var split = this.iconName.split("_");
        if (split.length > 0) {
            // The TPS travelers share the travelers' icons (UI_AvatarIcon_PlayerBoy/PlayerGirl), but
            // their abilities are in ConfigAvatar_AetherShadow/LumineShadow.
            this.name =
                    switch (this.id) {
                        case TPS_TRAVELER_MALE -> "AetherShadow";
                        case TPS_TRAVELER_FEMALE -> "LumineShadow";
                        default -> split[split.length - 1];
                    };

            var info = GameData.getAbilityEmbryoInfo().get(this.name);
            if (info != null) {
                this.abilities = new IntArrayList(info.getAbilities().length);
                for (var ability : info.getAbilities()) {
                    this.abilities.add(Utils.abilityHash(ability));
                    this.abilitieNames.add(ability);
                }
            }
        }

        // A cached AbilityEmbryos.json from before 7.0 has no entry for the TPS travelers. Without
        // Avatar_TPS_Innate_Ability the client shows no shooting HUD at all.
        if (this.id == TPS_TRAVELER_MALE || this.id == TPS_TRAVELER_FEMALE) {
            if (this.abilities == null) this.abilities = new IntArrayList();
            var tpsAbilities = new ArrayList<>(TPS_TRAVELER_ABILITIES);
            if (this.id == TPS_TRAVELER_FEMALE) tpsAbilities.add("Avatar_TPS_PlayerGirl_Ability");
            for (String ability : tpsAbilities) {
                int hash = Utils.abilityHash(ability);
                if (this.abilities.contains(hash)) continue;
                this.abilities.add(hash);
                this.abilitieNames.add(ability);
            }
        }

        // An avatar whose ability config is absent from the resource dump gets no embryo at all.
        // TeamManager appends to this list without a null check, so hand back an empty one rather
        // than null and let such an avatar simply have no abilities.
        if (this.abilities == null) {
            this.abilities = new IntArrayList();
        }
    }
}
