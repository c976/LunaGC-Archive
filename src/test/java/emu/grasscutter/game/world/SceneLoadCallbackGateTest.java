package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SceneLoadCallbackGateTest {
    @Test
    void callbacksRunOnceBeforeOrAfterSceneInitialization() {
        var gate = new SceneLoadCallbackGate();
        var count = new AtomicInteger();
        gate.whenComplete(count::incrementAndGet);
        assertEquals(0, count.get());
        gate.complete();
        gate.complete();
        assertEquals(1, count.get());
        gate.whenComplete(count::incrementAndGet);
        assertEquals(2, count.get());
    }

    @Test
    void concurrentScriptInitializationCannotDropSceneCallbacks() throws InterruptedException {
        var gate = new SceneLoadCallbackGate();
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        var workers = new ArrayList<Thread>();
        for (int i = 0; i < 24; i++) {
            var worker = new Thread(() -> {
                try {
                    release.await();
                    gate.whenComplete(count::incrementAndGet);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            workers.add(worker);
            worker.start();
        }
        release.countDown();
        gate.complete();
        for (var worker : workers) worker.join();
        assertEquals(workers.size(), count.get());
    }
}
