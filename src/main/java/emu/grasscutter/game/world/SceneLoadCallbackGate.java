package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot callback gate shared by the scene tick and asynchronous script
 * initialization threads. No callback can be lost between registration
 * and the transition to the loaded state.
 */
final class SceneLoadCallbackGate {
    private final List<Runnable> waiting = new ArrayList<>();
    private boolean completed;

    void whenComplete(Runnable callback) {
        if (callback == null) return;
        synchronized (this) {
            if (!completed) {
                waiting.add(callback);
                return;
            }
        }
        invoke(callback);
    }

    void complete() {
        final List<Runnable> ready;
        synchronized (this) {
            if (completed) return;
            completed = true;
            ready = List.copyOf(waiting);
            waiting.clear();
        }
        ready.forEach(SceneLoadCallbackGate::invoke);
    }

    private static void invoke(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            Grasscutter.getLogger().error("Scene finished-loading callback failed", exception);
        }
    }
}
