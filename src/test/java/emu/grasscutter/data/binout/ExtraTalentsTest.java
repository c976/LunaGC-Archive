package emu.grasscutter.data.binout;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.ResourceLoader.OpenConfigData;
import emu.grasscutter.data.excels.ProudSkillData;
import emu.grasscutter.data.excels.avatar.AvatarTalentData;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("GameData")
class ExtraTalentsTest {
    private static final String MIZUKI_CONFIG = "Mizuki_ExtraProundSkill";
    private Map<String, OpenConfigEntry> originalEntries;
    private Map<Integer, ProudSkillData> originalProudSkills;
    private Map<Integer, AvatarTalentData> originalTalents;
    private Object originalIndex;
    private Field indexField;

    @BeforeEach
    void isolateResources() throws Exception {
        originalEntries = new HashMap<>(GameData.getOpenConfigEntries());
        originalProudSkills = new HashMap<>(GameData.getProudSkillDataMap());
        originalTalents = new HashMap<>(GameData.getAvatarTalentDataMap());
        indexField = ExtraTalents.class.getDeclaredField("byOwner");
        indexField.setAccessible(true);
        originalIndex = indexField.get(null);

        GameData.getOpenConfigEntries().clear();
        GameData.getProudSkillDataMap().clear();
        GameData.getAvatarTalentDataMap().clear();
        indexField.set(null, null);
    }

    @AfterEach
    void restoreResources() throws Exception {
        GameData.getOpenConfigEntries().clear();
        GameData.getOpenConfigEntries().putAll(originalEntries);
        GameData.getProudSkillDataMap().clear();
        GameData.getProudSkillDataMap().putAll(originalProudSkills);
        GameData.getAvatarTalentDataMap().clear();
        GameData.getAvatarTalentDataMap().putAll(originalTalents);
        indexField.set(null, originalIndex);
    }

    @Test
    void normalizesOpenConfigOwnersAndTravelerElements() {
        assertEquals("", ExtraTalents.owner(null));
        assertEquals("", ExtraTalents.owner(""));
        assertEquals("Mizuki", ExtraTalents.owner("Mizuki"));
        assertEquals("Mizuki", ExtraTalents.owner(MIZUKI_CONFIG));
        assertEquals("Player_Ice", ExtraTalents.owner("Player_Ice_ProudSkill"));
        assertEquals("Player_Ice", ExtraTalents.owner("PlayerBoy_Ice_ProudSkill"));
        assertEquals("Player_Ice", ExtraTalents.owner("PlayerGirl_Ice"));
        assertEquals("Player_", ExtraTalents.owner("PlayerBoy_"));
        assertEquals("PlayerBoyish", ExtraTalents.owner("PlayerBoyish_Ice_ProudSkill"));
    }

    @Test
    void grantsOnlyTheEnabledUnreferencedMizukiAvatarAbility() {
        addEntry(MIZUKI_CONFIG, "Avatar_Mizuki_ExtraProudSkill");
        addEntry("Mizuki_AnotherExtraSkill", "Avatar_Mizuki_AnotherExtraSkill");
        addEntry("Diluc_ExtraProundSkill", "Avatar_Diluc_ExtraProudSkill");

        assertEquals(List.of(MIZUKI_CONFIG), ExtraTalents.forOwners(Set.of("Mizuki"), "Mizuki"));
        assertEquals(List.of(), ExtraTalents.forOwners(Set.of("Diluc"), "Diluc"));
        assertEquals(List.of(), ExtraTalents.forOwners(Set.of("Player_Ice"), "PlayerBoy"));
    }

    @Test
    void excludesEntriesAlreadyReferencedByAProudSkill() throws Exception {
        addEntry(MIZUKI_CONFIG, "Avatar_Mizuki_ExtraProudSkill");
        var proudSkill = new ProudSkillData();
        setOpenConfig(proudSkill, MIZUKI_CONFIG);
        GameData.getProudSkillDataMap().put(1, proudSkill);

        assertEquals(List.of(), ExtraTalents.forOwners(Set.of("Mizuki"), "Mizuki"));
    }

    @Test
    void excludesEntriesAlreadyReferencedByATalent() throws Exception {
        addEntry(MIZUKI_CONFIG, "Avatar_Mizuki_ExtraProudSkill");
        var talent = new AvatarTalentData();
        setOpenConfig(talent, MIZUKI_CONFIG);
        GameData.getAvatarTalentDataMap().put(1, talent);

        assertEquals(List.of(), ExtraTalents.forOwners(Set.of("Mizuki"), "Mizuki"));
    }

    @Test
    void requiresAtLeastOneAvatarAbility() {
        addEntry(MIZUKI_CONFIG, "Monster_Mizuki_ExtraProudSkill", "Avatarish_Mizuki");

        assertEquals(List.of(), ExtraTalents.forOwners(Set.of("Mizuki"), "Mizuki"));
    }

    @Test
    void ignoresEntriesWithoutAbilities() {
        addEntry(MIZUKI_CONFIG);

        assertEquals(List.of(), ExtraTalents.forOwners(Set.of("Mizuki"), "Mizuki"));
    }

    @Test
    void handlesNullResourceValuesNamesAndReferences() {
        GameData.getOpenConfigEntries().put("null-entry", null);
        GameData.getOpenConfigEntries().put("null-name", entry(null, "Avatar_Mizuki_Test"));
        GameData.getOpenConfigEntries().put("empty-name", entry("", "Avatar_Mizuki_Test"));
        GameData.getProudSkillDataMap().put(1, null);
        GameData.getProudSkillDataMap().put(2, new ProudSkillData());
        GameData.getAvatarTalentDataMap().put(1, null);
        GameData.getAvatarTalentDataMap().put(2, new AvatarTalentData());
        addEntry(MIZUKI_CONFIG, null, "Avatar_Mizuki_ExtraProudSkill");

        assertEquals(List.of(MIZUKI_CONFIG), ExtraTalents.forOwners(Set.of("Mizuki"), null));
    }

    @Test
    void emptyOwnersDoNotInitializeTheIndex() throws Exception {
        assertEquals(List.of(), ExtraTalents.forOwners(null, "Mizuki"));
        assertEquals(List.of(), ExtraTalents.forOwners(List.of(), "Mizuki"));
        assertNull(indexField.get(null));
    }

    @Test
    void nullAndRepeatedOwnersDoNotAddDuplicates() {
        addEntry(MIZUKI_CONFIG, "Avatar_Mizuki_ExtraProudSkill");

        assertEquals(
                List.of(MIZUKI_CONFIG),
                ExtraTalents.forOwners(Arrays.asList(null, "Mizuki", "Mizuki", ""), "Mizuki"));
    }

    @Test
    void keepsTravelerBodiesSeparatedWhenRoutingATravelerOwner() throws Exception {
        // This guard remains applicable if the explicit allowlist gains a traveler skill later.
        indexField.set(
                null,
                Map.of(
                        "Player_Ice",
                        List.of("PlayerBoy_Ice_Extra", "PlayerGirl_Ice_Extra", "Player_Ice_Extra")));

        assertEquals(
                List.of("PlayerBoy_Ice_Extra", "Player_Ice_Extra"),
                ExtraTalents.forOwners(Set.of("Player_Ice"), "PlayerBoy"));
        assertEquals(
                List.of("PlayerGirl_Ice_Extra", "Player_Ice_Extra"),
                ExtraTalents.forOwners(Set.of("Player_Ice"), "PlayerGirl"));
        assertEquals(
                List.of("Player_Ice_Extra"), ExtraTalents.forOwners(Set.of("Player_Ice"), null));
        assertEquals(
                List.of("Player_Ice_Extra"), ExtraTalents.forOwners(Set.of("Player_Ice"), "Mizuki"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishesACompleteDeeplyImmutableIndexToConcurrentReaders() throws Exception {
        addEntry(MIZUKI_CONFIG, "Avatar_Mizuki_ExtraProudSkill");
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(12);
        try {
            var futures = new ArrayList<Future<List<String>>>();
            for (int i = 0; i < 48; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    assertTrue(start.await(5, TimeUnit.SECONDS));
                                    return ExtraTalents.forOwners(Set.of("Mizuki"), "Mizuki");
                                }));
            }
            start.countDown();
            for (var future : futures) {
                var result = future.get(5, TimeUnit.SECONDS);
                assertEquals(List.of(MIZUKI_CONFIG), result);
                assertThrows(UnsupportedOperationException.class, () -> result.add("unexpected"));
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        var index = (Map<String, List<String>>) indexField.get(null);
        assertEquals(Map.of("Mizuki", List.of(MIZUKI_CONFIG)), index);
        assertThrows(UnsupportedOperationException.class, () -> index.put("Diluc", List.of("bad")));
        assertThrows(UnsupportedOperationException.class, () -> index.get("Mizuki").add("bad"));
    }

    private static void addEntry(String name, String... abilities) {
        GameData.getOpenConfigEntries().put(name, entry(name, abilities));
    }

    private static OpenConfigEntry entry(String name, String... abilities) {
        var data = new OpenConfigData[abilities.length];
        for (int i = 0; i < abilities.length; i++) {
            data[i] = new OpenConfigData();
            data[i].$type = "AddAbility";
            data[i].abilityName = abilities[i];
        }
        return new OpenConfigEntry(name, data);
    }

    private static void setOpenConfig(Object resource, String openConfig) throws Exception {
        var field = resource.getClass().getDeclaredField("openConfig");
        field.setAccessible(true);
        field.set(resource, openConfig);
    }
}
