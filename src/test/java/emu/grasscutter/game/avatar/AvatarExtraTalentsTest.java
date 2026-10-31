package emu.grasscutter.game.avatar;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.binout.ExtraTalents;
import emu.grasscutter.data.binout.OpenConfigEntry;
import emu.grasscutter.data.common.FightPropData;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.*;
import emu.grasscutter.game.props.ElementType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@ExtendWith(ServerResourceFixture.class)
@ResourceLock("GameData")
class AvatarExtraTalentsTest {
    private static final String EXTRA_ABILITY = "Avatar_Mizuki_ExtraProudSkill";
    private final Map<Map<?, ?>, Map<?, ?>> originals = new IdentityHashMap<>();
    private Field indexField;
    private Object originalIndex;

    @BeforeEach
    void isolateResources() throws Exception {
        isolate(GameData.getOpenConfigEntries());
        isolate(GameData.getProudSkillDataMap());
        isolate(GameData.getAvatarTalentDataMap());
        isolate(GameData.getAvatarSkillDataMap());
        isolate(GameData.getAvatarSkillDepotDataMap());
        indexField = ExtraTalents.class.getDeclaredField("byOwner");
        indexField.setAccessible(true);
        originalIndex = indexField.get(null);
        indexField.set(null, null);
        addEntry("Mizuki_ExtraProundSkill", EXTRA_ABILITY);
    }

    @AfterEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void restoreResources() throws Exception {
        for (var backup : originals.entrySet()) {
            Map resourceMap = backup.getKey();
            resourceMap.clear();
            resourceMap.putAll(backup.getValue());
        }
        indexField.set(null, originalIndex);
    }

    @ParameterizedTest
    @EnumSource(OwnerSource.class)
    void recalcGrantsMizukiExtraTalentFromEachOwnedConfigSource(OwnerSource source)
            throws Exception {
        var avatar = avatar("Mizuki", source);

        avatar.recalcStats(true);

        assertEquals(
                Set.of("Avatar_Mizuki_ExistingTalent", EXTRA_ABILITY), avatar.getExtraAbilityEmbryos());
        avatar.recalcStats(true);
        assertEquals(
                Set.of("Avatar_Mizuki_ExistingTalent", EXTRA_ABILITY), avatar.getExtraAbilityEmbryos());
    }

    @ParameterizedTest
    @EnumSource(OwnerSource.class)
    void recalcDoesNotGrantMizukiExtraTalentToAnotherAvatarsOwnedConfigs(OwnerSource source)
            throws Exception {
        var avatar = avatar("Diluc", source);

        avatar.recalcStats(true);

        assertEquals(Set.of("Avatar_Diluc_ExistingTalent"), avatar.getExtraAbilityEmbryos());
    }

    private Avatar avatar(String owner, OwnerSource source) throws Exception {
        var data = new AvatarData();
        set(data, "name", owner);
        set(data, "fetters", new ArrayList<Integer>());
        var depot = new AvatarSkillDepotData();
        set(depot, "id", 90001);
        set(depot, "skills", source == OwnerSource.SKILL ? List.of(90002) : List.of());
        set(depot, "talents", source == OwnerSource.CONSTELLATION ? List.of(90003) : List.of());
        set(depot, "elementType", ElementType.None);
        set(depot, "questProudSkillGroupIds", new IntArrayList());
        set(depot, "inherentProudSkillOpens", List.of());
        String ownedConfig = owner + "_ExistingTalent";
        addEntry(ownedConfig, "Avatar_" + owner + "_ExistingTalent");

        if (source == OwnerSource.PASSIVE) {
            var passive = new AvatarSkillDepotData.InherentProudSkillOpens();
            set(passive, "proudSkillGroupId", 900);
            set(depot, "inherentProudSkillOpens", List.of(passive));
            addProudSkill(90001, ownedConfig);
        } else if (source == OwnerSource.SKILL) {
            var skill = new AvatarSkillData();
            set(skill, "proudSkillGroupId", 900);
            GameData.getAvatarSkillDataMap().put(90002, skill);
            addProudSkill(90001, ownedConfig);
        } else {
            var talent = new AvatarTalentData();
            set(talent, "openConfig", ownedConfig);
            GameData.getAvatarTalentDataMap().put(90003, talent);
        }
        GameData.getAvatarSkillDepotDataMap().put(depot.getId(), depot);

        var avatar = new Avatar();
        set(avatar, "avatarData", data);
        set(avatar, "skillDepot", depot);
        set(avatar, "skillDepotId", depot.getId());
        set(avatar, "proudSkillList", new HashSet<Integer>());
        set(avatar, "talentIdList", new HashSet<>(List.of(90003)));
        return avatar;
    }

    private void isolate(Map<?, ?> resourceMap) {
        originals.put(resourceMap, new HashMap<>(resourceMap));
        resourceMap.clear();
    }

    private static void addProudSkill(int id, String config) throws Exception {
        var proudSkill = new ProudSkillData();
        set(proudSkill, "openConfig", config);
        set(proudSkill, "addProps", new FightPropData[0]);
        GameData.getProudSkillDataMap().put(id, proudSkill);
    }

    private static void addEntry(String name, String ability) {
        var entry = new OpenConfigData();
        entry.$type = "AddAbility";
        entry.abilityName = ability;
        GameData.getOpenConfigEntries().put(name, new OpenConfigEntry(name, new OpenConfigData[] {entry}));
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private enum OwnerSource {
        PASSIVE,
        SKILL,
        CONSTELLATION
    }
}
