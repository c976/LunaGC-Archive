package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Native quest trial avatars must survive sparse custom activity overrides. */
final class TrialAvatarSelectionTest {
    @Test
    void nativeAmberTrialRemainsAvailableWhenUnrelatedCustomDataExists() {
        assertEquals(
                List.of(10000021, 5),
                TeamManager.selectTrialAvatarParams(null, List.of(10000021, 5)));
    }

    @Test
    void explicitCustomEntryOverridesOnlyItsOwnTrialId() {
        assertEquals(
                List.of(10000021, 8),
                TeamManager.selectTrialAvatarParams(
                        List.of("10000021;8"), List.of(10000021, 5)));
    }

    @Test
    void missingBothSourcesHasNoAvatar() {
        assertEquals(List.of(), TeamManager.selectTrialAvatarParams(null, null));
    }
}
