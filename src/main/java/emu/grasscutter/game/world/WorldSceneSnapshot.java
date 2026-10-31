package emu.grasscutter.game.world;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class WorldSceneSnapshot {
    private WorldSceneSnapshot() {}

    static <T> void forEachOutsideMonitor(
            Object monitor,
            Supplier<? extends Collection<? extends T>> values,
            Consumer<? super T> action) {
        List<T> snapshot;
        synchronized (monitor) {
            snapshot = List.copyOf(values.get());
        }
        snapshot.forEach(action);
    }
}
