package emu.grasscutter.data.binout.config;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;
@Data
public class ConfigGlobalCombat {
    // Keys obfuscated in the newer resource dump are accepted as alternates, matched to the
    // named 4.0 file by their contents.
    @SerializedName(value = "defaultAbilities", alternate = {"EHNGNAOKIPB"})
    private DefaultAbilities defaultAbilities;
    // TODO: Add more indices

    public boolean isDefaultAbilitiesMissing() {
        return this.defaultAbilities == null;
    }

    /**
     * Never null: a resource dump whose keys were re-obfuscated leaves the field unset, and
     * every entity's ability setup (the world entity first, during login) reads it.
     */
    public DefaultAbilities getDefaultAbilities() {
        if (this.defaultAbilities == null) this.defaultAbilities = new DefaultAbilities();
        return this.defaultAbilities;
    }

    @Data
    public static class DefaultAbilities {
        @SerializedName(value = "monterEliteAbilityName", alternate = {"CIPPBKBJJFH"})
        private String monterEliteAbilityName;
        @SerializedName(value = "nonHumanoidMoveAbilities", alternate = {"LDHLMPMOPKL"})
        private List<String> nonHumanoidMoveAbilities;
        @SerializedName(value = "levelDefaultAbilities", alternate = {"BJIFBDHAJMN"})
        private List<String> levelDefaultAbilities;
        @SerializedName(value = "levelElementAbilities", alternate = {"NEEHFCBBAKF"})
        private List<String> levelElementAbilities;
        @SerializedName(value = "levelItemAbilities", alternate = {"NFEJCNKAEEK"})
        private List<String> levelItemAbilities;
        @SerializedName(value = "levelSBuffAbilities", alternate = {"GBDLFMJEKIC"})
        private List<String> levelSBuffAbilities;
        @SerializedName(value = "defaultMPLevelAbilities", alternate = {"NHHFOOAIHIA"})
        private List<String> defaultMPLevelAbilities;
        @SerializedName(value = "defaultAvatarAbilities", alternate = {"PPLPBDDJMGM"})
        private List<String> defaultAvatarAbilities;
        // Obfuscated in the 7.0 dump. Holds TeamAbility_MoonPhase and the other party-wide
        // abilities, so without the alternate the team entity was built with none of them.
        @SerializedName(value = "defaultTeamAbilities", alternate = {"GHMJEDOALPL", "GPNPNBPEDOB"})
        private List<String> defaultTeamAbilities;

        public List<String> getNonHumanoidMoveAbilities() { return orEmpty(this.nonHumanoidMoveAbilities); }
        public List<String> getLevelDefaultAbilities() { return orEmpty(this.levelDefaultAbilities); }
        public List<String> getLevelElementAbilities() { return orEmpty(this.levelElementAbilities); }
        public List<String> getLevelItemAbilities() { return orEmpty(this.levelItemAbilities); }
        public List<String> getLevelSBuffAbilities() { return orEmpty(this.levelSBuffAbilities); }
        public List<String> getDefaultMPLevelAbilities() { return orEmpty(this.defaultMPLevelAbilities); }
        public List<String> getDefaultAvatarAbilities() { return orEmpty(this.defaultAvatarAbilities); }
        public List<String> getDefaultTeamAbilities() { return orEmpty(this.defaultTeamAbilities); }

        private static List<String> orEmpty(List<String> list) {
            return list != null ? list : List.of();
        }
    }
}
