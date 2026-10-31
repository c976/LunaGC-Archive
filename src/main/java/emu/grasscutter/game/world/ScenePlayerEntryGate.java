package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Delivers callbacks when a specific player is in this scene.
 * Scene loading can finish before a player returns from a dungeon; a one-shot
 * scene-loaded callback by itself cannot represent that transition.
 */
final class ScenePlayerEntryGate {
    private final Set<Integer> present = new HashSet<>();
    private final Map<Integer, List<Runnable>> waiting = new HashMap<>();

    void whenPresent(int playerUid, Runnable callback) {
        if (playerUid <= 0 || callback == null) return;
        synchronized (this) {
            if (!present.contains(playerUid)) {
                waiting.computeIfAbsent(playerUid, ignored -> new ArrayList<>()).add(callback);
                return;
            }
        }
        invoke(callback);
    }

    void enter(int playerUid) {
        final List<Runnable> ready;
        synchronized (this) {
            present.add(playerUid);
            ready = waiting.remove(playerUid);
        }
        if (ready != null) ready.forEach(ScenePlayerEntryGate::invoke);
    }

    synchronized void leave(int playerUid) {
        present.remove(playerUid);
    }

    private static void invoke(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            Grasscutter.getLogger().error("Scene player-entry callback failed", exception);
        }
    }
}
