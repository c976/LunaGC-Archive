package emu.grasscutter.scripts;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SceneScriptInitGateTest {
    @Test
    void runsDeferredListenersExactlyOnceOnSuccess() {
        var gate = new SceneScriptInitGate();
        var seen = new ArrayList<Boolean>();
        gate.whenComplete(seen::add);
        assertTrue(seen.isEmpty());
        gate.complete(true);
        gate.complete(false);
        assertEquals(List.of(true), seen);
    }

    @Test
    void failedOrDestroyedInitializationNotifiesWaiters() {
        var gate = new SceneScriptInitGate();
        var seen = new ArrayList<Boolean>();
        gate.whenComplete(seen::add);
        gate.complete(false);
        assertEquals(List.of(false), seen);
    }

    @Test
    void lateListenerUsesTerminalOutcomeWithoutAnotherRetry() {
        var gate = new SceneScriptInitGate();
        gate.complete(true);
        var seen = new ArrayList<Boolean>();
        gate.whenComplete(seen::add);
        assertEquals(List.of(true), seen);
    }
}
