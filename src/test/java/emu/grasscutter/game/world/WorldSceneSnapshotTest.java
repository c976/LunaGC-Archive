package emu.grasscutter.game.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorldSceneSnapshotTest {
    @Test
    void callbackRunsAfterCollectionMonitorIsReleased() throws Exception {
        var collectionMonitor = new Object();
        var sceneMonitor = new Object();
        var scenes = new ArrayList<>(List.of("scene"));
        var sceneHeld = new CountDownLatch(1);
        var callbackEntered = new CountDownLatch(1);
        var collectionMonitorAcquired = new CountDownLatch(1);
        var tickCompleted = new CountDownLatch(1);

        var leaveThread =
                new Thread(
                        () -> {
                            synchronized (sceneMonitor) {
                                sceneHeld.countDown();
                                await(callbackEntered);
                                synchronized (collectionMonitor) {
                                    collectionMonitorAcquired.countDown();
                                }
                            }
                        },
                        "r3-leave");
        leaveThread.setDaemon(true);
        leaveThread.start();
        assertTrue(sceneHeld.await(1, TimeUnit.SECONDS));

        var tickThread =
                new Thread(
                        () -> {
                            WorldSceneSnapshot.forEachOutsideMonitor(
                                    collectionMonitor,
                                    () -> scenes,
                                    ignored -> {
                                        callbackEntered.countDown();
                                        synchronized (sceneMonitor) {
                                            // Models Scene.onTick entering a synchronized Scene method.
                                        }
                                    });
                            tickCompleted.countDown();
                        },
                        "r3-tick");
        tickThread.setDaemon(true);
        tickThread.start();

        assertTrue(callbackEntered.await(1, TimeUnit.SECONDS));
        assertTrue(
                collectionMonitorAcquired.await(1, TimeUnit.SECONDS),
                "scene callback still holds the collection monitor");
        assertTrue(tickCompleted.await(1, TimeUnit.SECONDS));

        leaveThread.join(1000);
        tickThread.join(1000);
        assertTrue(!leaveThread.isAlive() && !tickThread.isAlive());
    }

    @Test
    void callbacksUseStableSnapshotWhenSourceChanges() {
        var collectionMonitor = new Object();
        var scenes = new ArrayList<>(List.of("first", "second"));
        var visited = new ArrayList<String>();

        WorldSceneSnapshot.forEachOutsideMonitor(
                collectionMonitor,
                () -> scenes,
                scene -> {
                    visited.add(scene);
                    if (scene.equals("first")) {
                        synchronized (collectionMonitor) {
                            scenes.remove("second");
                        }
                    }
                });

        assertEquals(List.of("first", "second"), visited);
        assertEquals(List.of("first"), scenes);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for test coordination");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
