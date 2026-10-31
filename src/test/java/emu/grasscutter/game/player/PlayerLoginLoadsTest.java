package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class PlayerLoginLoadsTest {
    @Test
    void loginWaitsForBattlePassAndReportsItsFailure() throws Exception {
        var pending = new ObservedLoad();
        var player = new Player();
        var login = CompletableFuture.runAsync(() -> player.awaitLoginLoads(
                ready(), ready(), ready(), ready(), pending, ready()));

        assertTrue(pending.awaited.await(2, TimeUnit.SECONDS));
        assertFalse(login.isDone());
        pending.complete(null);
        login.get(2, TimeUnit.SECONDS);

        var failure = new CompletableFuture<Void>();
        failure.completeExceptionally(new IllegalStateException("slow query failed"));
        assertTrue(assertThrows(IllegalStateException.class, () -> player.awaitLoginLoads(
                ready(), ready(), ready(), ready(), failure, ready()))
                .getMessage().contains("battle pass"));
    }

    @Test
    void loginWaitsForStoredQuestsAndReportsItsFailure() throws Exception {
        var pending = new ObservedLoad();
        var player = new Player();
        var login = CompletableFuture.runAsync(() -> player.awaitLoginLoads(
                ready(), ready(), ready(), pending, ready(), ready()));

        assertTrue(pending.awaited.await(2, TimeUnit.SECONDS));
        assertFalse(login.isDone());
        pending.complete(null);
        login.get(2, TimeUnit.SECONDS);

        var failure = new CompletableFuture<Void>();
        failure.completeExceptionally(new IllegalStateException("slow query failed"));
        assertTrue(assertThrows(IllegalStateException.class, () -> player.awaitLoginLoads(
                ready(), ready(), ready(), failure, ready(), ready()))
                .getMessage().contains("quests"));
    }

    private static CompletableFuture<Void> ready() {
        return CompletableFuture.completedFuture(null);
    }

    private static final class ObservedLoad extends CompletableFuture<Void> {
        final CountDownLatch awaited = new CountDownLatch(1);

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            awaited.countDown();
            return super.get(timeout, unit);
        }
    }
}
