package emu.grasscutter.game.entity.gadget;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.net.proto.WorktopOptionNotifyOuterClass.WorktopOptionNotify;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public final class GadgetWorktopConcurrencyTest {
    @Test
    void emptyOptionsSerializeAsAnEmptyWorktop() {
        var worktop = new GadgetWorktop(null);
        var gadgetInfo = SceneGadgetInfo.newBuilder();

        worktop.onBuildProto(gadgetInfo);

        assertTrue(gadgetInfo.hasWorktop());
        assertEquals(List.of(), gadgetInfo.getWorktop().getOptionListList());
        assertEquals(List.of(), notifyOptions(worktop));
        worktop.removeWorktopOption(7);
        assertTrue(worktop.getWorktopOptions().isEmpty());
    }

    @Test
    void duplicateAddsRemovalAndLiveGetterClearKeepExistingSemantics() {
        var worktop = new GadgetWorktop(null);
        var liveOptions = worktop.getWorktopOptions();
        worktop.addWorktopOptions(new int[] {7, 175, 175, 176});

        assertSame(liveOptions, worktop.getWorktopOptions());
        assertEquals(Set.of(7, 175, 176), liveOptions);
        worktop.removeWorktopOption(7);
        worktop.removeWorktopOption(999);
        assertEquals(Set.of(175, 176), new HashSet<>(notifyOptions(worktop)));

        liveOptions.clear();

        assertTrue(worktop.getWorktopOptions().isEmpty());
        assertTrue(notifyOptions(worktop).isEmpty());
    }

    @Test
    void iteratorKeepsOriginalSnapshotAfterConcurrentClearAndAdd() throws Exception {
        var worktop = new GadgetWorktop(null);
        worktop.addWorktopOptions(new int[] {175, 176});
        var iterator = worktop.getWorktopOptions().iterator();

        runAsync(() -> {
            worktop.getWorktopOptions().clear();
            worktop.addWorktopOptions(new int[] {177});
        }).get(5, TimeUnit.SECONDS);

        var originalOptions = new HashSet<Integer>();
        iterator.forEachRemaining(originalOptions::add);
        assertEquals(Set.of(175, 176), originalOptions);
        assertEquals(Set.of(177), worktop.getWorktopOptions());
    }

    @Test
    void concurrentFirstAddsDoNotReplaceOrLoseAnotherWritersOptions() throws Exception {
        var worktop = new GadgetWorktop(null);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var writers = new ArrayList<CompletableFuture<Void>>();
        for (int writer = 0; writer < 8; writer++) {
            int first = writer * 250;
            writers.add(runAsync(() -> {
                ready.countDown();
                await(start);
                worktop.addWorktopOptions(IntStream.range(first, first + 250).toArray());
            }));
        }
        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
        for (var writer : writers) writer.get(10, TimeUnit.SECONDS);

        assertEquals(2000, worktop.getWorktopOptions().size());
        for (int option = 0; option < 2000; option++) {
            assertTrue(worktop.getWorktopOptions().contains(option));
        }
    }

    @Test
    void protocolSnapshotsDoNotObservePartiallyPublishedAddBatches() throws Exception {
        var worktop = new GadgetWorktop(null);
        var start = new CountDownLatch(1);
        int[] batch = IntStream.range(1, 65).toArray();
        var writer = runAsync(() -> {
            await(start);
            for (int index = 0; index < 3000; index++) {
                worktop.getWorktopOptions().clear();
                worktop.addWorktopOptions(batch);
            }
        });
        var reader = runAsync(() -> {
            await(start);
            for (int index = 0; index < 10000; index++) {
                var snapshot = notifyOptions(worktop);
                assertTrue(snapshot.isEmpty() || snapshot.size() == batch.length,
                        "One addWorktopOptions batch must become visible as a whole");
                assertEquals(snapshot.size(), new HashSet<>(snapshot).size());
            }
        });
        start.countDown();
        writer.get(10, TimeUnit.SECONDS);
        reader.get(10, TimeUnit.SECONDS);
        assertEquals(64, worktop.getWorktopOptions().size());
    }

    @Test
    void concurrentMutationsAndBothProtocolReadersKeepValidOptionSnapshots() throws Exception {
        var worktop = new GadgetWorktop(null);
        var start = new CountDownLatch(1);
        var snapshots = new AtomicInteger();
        var tasks = new ArrayList<CompletableFuture<Void>>();
        for (int writer = 0; writer < 2; writer++) {
            int[] options = writer == 0 ? new int[] {7, 175, 176} : new int[] {9, 10, 177};
            tasks.add(runAsync(() -> {
                await(start);
                for (int index = 0; index < 5000; index++) {
                    worktop.addWorktopOptions(options);
                    worktop.removeWorktopOption(options[0]);
                    worktop.getWorktopOptions().clear();
                }
            }));
        }
        for (int reader = 0; reader < 2; reader++) {
            tasks.add(runAsync(() -> {
                await(start);
                for (int index = 0; index < 10000; index++) {
                    var gadgetInfo = SceneGadgetInfo.newBuilder();
                    worktop.onBuildProto(gadgetInfo);
                    assertTrue(gadgetInfo.hasWorktop());
                    assertValidOptions(gadgetInfo.getWorktop().getOptionListList());
                    assertValidOptions(notifyOptions(worktop));
                    snapshots.incrementAndGet();
                }
            }));
        }
        start.countDown();
        for (var task : tasks) task.get(15, TimeUnit.SECONDS);
        assertEquals(20000, snapshots.get());

        worktop.getWorktopOptions().clear();
        worktop.addWorktopOptions(new int[] {175, 176, 175});
        assertEquals(Set.of(175, 176), worktop.getWorktopOptions());
        assertEquals(Set.of(175, 176), new HashSet<>(notifyOptions(worktop)));
    }

    private static List<Integer> notifyOptions(GadgetWorktop worktop) {
        // Match PacketWorktopOptionNotify's collection-to-protobuf boundary without constructing
        // a resource-backed EntityGadget or starting the server fixture.
        return WorktopOptionNotify.newBuilder()
                .addAllOptionList(worktop.getWorktopOptions())
                .build()
                .getOptionListList();
    }

    private static void assertValidOptions(List<Integer> options) {
        assertEquals(options.size(), new HashSet<>(options).size());
        assertTrue(Set.of(7, 9, 10, 175, 176, 177).containsAll(options));
    }

    private static CompletableFuture<Void> runAsync(Runnable action) {
        var result = new CompletableFuture<Void>();
        var thread = new Thread(() -> {
            try {
                action.run();
                result.complete(null);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        }, "worktop-option-concurrency-test");
        thread.setDaemon(true);
        thread.start();
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Timed out waiting for the test thread");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
