package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ScenePlayerEntryGateTest {
    @Test
    void loadedButEmptySceneMustWaitForDungeonReturn() {
        var gate = new ScenePlayerEntryGate();
        var calls = new AtomicInteger();
        gate.whenPresent(10001, calls::incrementAndGet);
        assertEquals(0, calls.get(), "scene-loaded alone must not run a quest finish event");
        gate.enter(10001);
        assertEquals(1, calls.get());
        gate.leave(10001);
        gate.enter(10001);
        assertEquals(1, calls.get(), "one-shot callback must not replay");
    }

    @Test
    void registrationAfterPlayerArrivalRunsImmediately() {
        var gate = new ScenePlayerEntryGate();
        gate.enter(10001);
        var calls = new AtomicInteger();
        gate.whenPresent(10001, calls::incrementAndGet);
        assertEquals(1, calls.get());
        gate.whenPresent(10002, calls::incrementAndGet);
        assertEquals(1, calls.get(), "another player must not release this callback");
        gate.enter(10002);
        assertEquals(2, calls.get());
    }

    @Test
    void concurrentQuestCallbacksCannotBeLostOnSceneTransition() throws InterruptedException {
        var gate = new ScenePlayerEntryGate();
        var start = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var workers = new ArrayList<Thread>();
        for (int n = 0; n < 24; n++) {
            var worker = new Thread(() -> {
                try {
                    start.await();
                    gate.whenPresent(10001, calls::incrementAndGet);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        gate.enter(10001);
        for (Thread worker : workers) worker.join();
        assertEquals(24, calls.get());
    }
}
