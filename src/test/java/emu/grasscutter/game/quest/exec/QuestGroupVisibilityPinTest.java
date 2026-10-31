package emu.grasscutter.game.quest.exec;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Suite resets and failed refreshes must not permanently pin a scene group. */
final class QuestGroupVisibilityPinTest {
    @Test
    void activeOverridesAreProtectedAgainstVisibilityUnload() {
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(false, true, 2, false));
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(true, true, 1, false));
    }

    @Test
    void resetUnpinsUnlessAnotherQuestStillOwnsTheGroup() {
        assertFalse(ExecRefreshGroupSuite.pinAfterRefresh(true, true, 0, false));
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(true, true, 0, true));
    }

    @Test
    void failedRefreshNeverChangesPreviousPinState() {
        assertTrue(ExecRefreshGroupSuite.pinAfterRefresh(true, false, 0, false));
        assertFalse(ExecRefreshGroupSuite.pinAfterRefresh(false, false, 2, false));
    }

    @Test
    void failedOrThrowingRefreshRestoresOldVisibilityPin() {
        var group = emu.grasscutter.scripts.data.SceneGroup.of(133003002);
        assertFalse(ExecRefreshGroupSuite.refreshWithVisibilityPin(
                group, 2, () -> {
                    assertTrue(group.dontUnload, "temporary pin must cover refresh");
                    return false;
                }, () -> false));
        assertFalse(group.dontUnload);

        assertThrows(IllegalStateException.class, () ->
                ExecRefreshGroupSuite.refreshWithVisibilityPin(
                        group, 2, () -> {
                            assertTrue(group.dontUnload);
                            throw new IllegalStateException("Lua refresh failed");
                        }, () -> false));
        assertFalse(group.dontUnload, "exception must not leak a temporary pin");

        group.dontUnload = true;
        assertThrows(IllegalStateException.class, () ->
                ExecRefreshGroupSuite.refreshWithVisibilityPin(
                        group, 0, () -> {
                            throw new IllegalStateException("Lua reset failed");
                        }, () -> false));
        assertTrue(group.dontUnload, "failed reset must preserve old pin");
    }

    @Test
    void successfulResetCanReleasePinOrKeepAnotherQuestOwner() {
        var group = emu.grasscutter.scripts.data.SceneGroup.of(133003002);
        group.dontUnload = true;
        assertTrue(ExecRefreshGroupSuite.refreshWithVisibilityPin(
                group, 0, () -> true, () -> false));
        assertFalse(group.dontUnload);

        group.dontUnload = true;
        assertTrue(ExecRefreshGroupSuite.refreshWithVisibilityPin(
                group, 0, () -> true, () -> true));
        assertTrue(group.dontUnload);

        group.dontUnload = false;
        assertTrue(ExecRefreshGroupSuite.refreshWithVisibilityPin(
                group, 2, () -> true, () -> {
                    fail("only resets should check another owner");
                    return false;
                }));
        assertTrue(group.dontUnload);
    }

    @Test
    void ownershipCheckExceptionDoesNotStrandNewTemporaryPin() {
        var group = emu.grasscutter.scripts.data.SceneGroup.of(133003002);
        assertThrows(IllegalStateException.class, () ->
                ExecRefreshGroupSuite.refreshWithVisibilityPin(
                        group, 0, () -> true, () -> {
                            throw new IllegalStateException("lookup failed");
                        }));
        assertFalse(group.dontUnload);
    }

    @Test
    void visibilityPinIsVisibleAcrossSceneAndQuestThreads() throws Exception {
        var field = emu.grasscutter.scripts.data.SceneGroup.class.getField("dontUnload");
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.getModifiers()),
                "quest thread writes and scene visibility reads require a volatile pin");
    }
}
