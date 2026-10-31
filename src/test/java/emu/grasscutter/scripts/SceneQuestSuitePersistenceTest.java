package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.quest.QuestGroupSuite;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SceneQuestSuitePersistenceTest {
    @Test
    void lastAppliedSuiteWinsAfterMultipleQuestRefreshes() {
        var saved = new ArrayList<QuestGroupSuite>();
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 1);
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 2);
        assertEquals(1, saved.size(), "reconnect must not replay the outdated suite");
        assertEquals(2, saved.get(0).getSuite());
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 2);
        assertEquals(1, saved.size(), "repeated start events must be idempotent");
    }

    @Test
    void revertingToInitSuiteRemovesOnlyMatchingQuestOverride() {
        var saved = new ArrayList<QuestGroupSuite>();
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 2);
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003136, 1);
        SceneScriptManager.rememberQuestGroupSuite(saved, 4, 133003002, 1);
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 0);
        assertEquals(2, saved.size());
        assertTrue(saved.stream().anyMatch(x -> x.getScene() == 3 && x.getGroup() == 133003136));
        assertTrue(saved.stream().anyMatch(x -> x.getScene() == 4 && x.getGroup() == 133003002));
    }

    @Test
    void repeatedOrRedundantResetDoesNotDirtyPersistedQuestState() {
        var saved = new ArrayList<QuestGroupSuite>();
        assertFalse(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 0));
        assertTrue(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 1));
        assertFalse(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 1));
        assertTrue(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 2));
        assertFalse(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 2));
        assertTrue(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 0));
        assertFalse(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 0));
        assertTrue(saved.isEmpty());
    }

    @Test
    void legacyDuplicateEntriesAreConsideredDirtyEvenIfLatestMatches() {
        var saved = new ArrayList<QuestGroupSuite>();
        saved.add(QuestGroupSuite.of().scene(3).group(133003002).suite(1).build());
        saved.add(QuestGroupSuite.of().scene(3).group(133003002).suite(2).build());
        assertTrue(SceneScriptManager.rememberQuestGroupSuite(saved, 3, 133003002, 2));
        assertEquals(1, saved.size());
        assertEquals(2, saved.get(0).getSuite());
    }

    @Test
    void invalidOrMissingPersistenceListIsIgnored() {
        var saved = new ArrayList<QuestGroupSuite>();
        SceneScriptManager.rememberQuestGroupSuite(null, 3, 1, 2);
        SceneScriptManager.rememberQuestGroupSuite(saved, 0, 1, 2);
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 0, 2);
        SceneScriptManager.rememberQuestGroupSuite(saved, 3, 1, -1);
        assertTrue(saved.isEmpty());
    }
}
