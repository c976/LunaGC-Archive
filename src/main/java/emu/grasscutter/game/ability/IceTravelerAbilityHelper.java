package emu.grasscutter.game.ability;

import emu.grasscutter.GameConstants;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.net.proto.AbilityControlBlockOuterClass.AbilityControlBlock;
import emu.grasscutter.net.proto.AbilityEmbryoOuterClass.AbilityEmbryo;
import emu.grasscutter.utils.Utils;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Cryo Traveler (and any avatar whose SkillUpgrade openConfig uses AddAbility):
 * StarSuperconducted_Attack* are only declared in Player*Ice_SkillUpgrade_1, not in the
 * skill-depot ability group. Without those embryos in AbilityControlBlock, infusion
 * pattern replacement has no attack ability → visual only, zero hits.
 */
public final class IceTravelerAbilityHelper {
    private IceTravelerAbilityHelper() {}

    /** Collect AddAbility names from current skill openConfigs. */
    public static Set<String> skillUpgradeAddAbilities(Avatar avatar) {
        Set<String> out = new LinkedHashSet<>();
        if (avatar == null) return out;
        var depot = GameData.getAvatarSkillDepotDataMap().get(avatar.getSkillDepotId());
        if (depot == null) return out;
        var skillLevelMap = avatar.getSkillLevelMap();
        depot.getSkillsAndEnergySkill()
                .forEach(
                        skillId -> {
                            var skillCfg = GameData.getAvatarSkillDataMap().get(skillId);
                            if (skillCfg == null || skillCfg.getProudSkillGroupId() == 0) return;
                            int level = skillLevelMap.getOrDefault(skillId, 1);
                            var proud =
                                    GameData.getProudSkillDataMap()
                                            .get(skillCfg.getProudSkillGroupId() * 100 + level);
                            if (proud == null || proud.getOpenConfig() == null) return;
                            var entry = GameData.getOpenConfigEntries().get(proud.getOpenConfig());
                            if (entry == null || entry.getAddAbilities() == null) return;
                            for (String name : entry.getAddAbilities()) {
                                if (name != null && !name.isEmpty()) out.add(name);
                            }
                        });
        return out;
    }

    /**
     * Append missing skill-upgrade AddAbility embryos. Returns the next embryo id to use.
     */
    public static int appendSkillUpgradeEmbryos(
            Avatar avatar, AbilityControlBlock.Builder block, int embryoId) {
        if (avatar == null || block == null) return embryoId;
        Set<String> already = avatar.getExtraAbilityEmbryos();
        for (String name : skillUpgradeAddAbilities(avatar)) {
            if (already != null && already.contains(name)) continue;
            AbilityEmbryo emb =
                    AbilityEmbryo.newBuilder()
                            .setAbilityId(++embryoId)
                            .setAbilityNameHash(Utils.abilityHash(name))
                            .setAbilityOverrideNameHash(GameConstants.DEFAULT_ABILITY_NAME)
                            .build();
            block.addAbilityEmbryoList(emb);
        }
        return embryoId;
    }
}
