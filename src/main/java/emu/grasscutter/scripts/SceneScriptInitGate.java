package emu.grasscutter.scripts;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** One-shot scene script initialization outcome. A late subscriber sees the same result. */
final class SceneScriptInitGate {
    private final CompletableFuture<Boolean> outcome = new CompletableFuture<>();

    void complete(boolean initialized) {
        outcome.complete(initialized);
    }

    void whenComplete(Consumer<Boolean> callback) {
        outcome.thenAccept(callback);
    }
}
