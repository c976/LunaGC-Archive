package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;
import dev.morphia.query.Query;
import emu.grasscutter.ServerResourceFixture;
import emu.grasscutter.game.achievement.Achievements;
import emu.grasscutter.game.player.Player;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("integration") // Replaces the process-wide datastore, like AccountPersistenceTest.
@ExtendWith(ServerResourceFixture.class)
class AchievementReadFailureTest {
    @Test
    void decodingFailureCannotDeleteOrRecreateRewardHistory() throws Exception {
        var expected = new IllegalArgumentException("Cannot decode old achievement status");
        var mutations = new AtomicInteger();
        Object query = Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[] {Query.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("filter")) return proxy;
                    if (method.getName().equals("first")) throw expected;
                    mutations.incrementAndGet();
                    throw new AssertionError("Unexpected query mutation: " + method.getName());
                });
        var store = (Datastore) Proxy.newProxyInstance(Datastore.class.getClassLoader(),
                new Class<?>[] {Datastore.class}, (proxy, method, args) -> {
                    if (method.getName().equals("find")) return query;
                    mutations.incrementAndGet();
                    throw new AssertionError("Read failure attempted " + method.getName());
                });
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var previous = field.get(null);
        try {
            field.set(null, store);
            var player = new Player();
            player.setUid(12345);
            assertSame(expected, assertThrows(IllegalArgumentException.class,
                    () -> Achievements.getByPlayer(player)));
            assertEquals(0, mutations.get(), "Existing history must survive a failed read");
            assertNull(player.getAchievements());
        } finally {
            field.set(null, previous);
        }
    }
}
