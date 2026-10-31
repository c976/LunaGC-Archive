package emu.grasscutter.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QuestAbilityFilterTest {
    @Test
    void questAbilityDirectoryIsRecognised() {
        assertTrue(ResourceLoader.isQuestAbilityPath(
                Path.of("resources", "BinOutput", "Ability", "Temp", "QuestAbilities", "ConfigAbility_Avatar_Quest.json")));
        assertFalse(ResourceLoader.isQuestAbilityPath(
                Path.of("resources", "BinOutput", "Ability", "Temp", "AvatarAbilities", "ConfigAbility_Avatar_Nahida.json")));
        // Only a whole path segment counts, not a file that merely mentions it.
        assertFalse(ResourceLoader.isQuestAbilityPath(
                Path.of("resources", "BinOutput", "Ability", "Temp", "QuestAbilities_Old.json")));
    }

    @Test
    void questNamedAbilitiesAreSkippedWithoutTheDirectory() {
        assertTrue(ResourceLoader.isQuestAbility("Avatar_Columbina_MainQuest"));
        assertFalse(ResourceLoader.isQuestAbility("Avatar_Nahida_PermanentSkill_1"));
    }
}
