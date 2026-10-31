package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import dev.morphia.Datastore;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Account credentials must be committed before they are returned to the HTTP caller. */
@Tag("integration")
public class AccountPersistenceTest {
    @Test
    public void generatedTokenWaitsForDatabaseWrite() throws Exception {
        var entered = new CountDownLatch(1);
        var complete = new CountDownLatch(1);
        var persisted = new AtomicReference<String>();

        Datastore datastore =
                fakeDatastore(
                        account -> {
                            entered.countDown();
                            if (!complete.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting for test release");
                            }
                            persisted.set(account.getToken());
                        });

        try (var ignored = installDatastore(datastore)) {
            var issued =
                    CompletableFuture.supplyAsync(
                            () -> {
                                var account = new Account();
                                account.setId("account-1");
                                return account.generateLoginToken();
                            });

            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> issued.get(100, TimeUnit.MILLISECONDS));
            } finally {
                complete.countDown();
            }

            String issuedToken = issued.get(5, TimeUnit.SECONDS);
            assertNotNull(issuedToken);
            assertEquals(issuedToken, persisted.get());
        }
    }

    @Test
    public void aFailedTokenWriteFailsTheLogin() throws Exception {
        Datastore datastore =
                fakeDatastore(
                        account -> {
                            throw new IllegalStateException("MongoDB write failed");
                        });

        try (var ignored = installDatastore(datastore)) {
            var account = new Account();
            account.setId("account-2");
            assertThrows(IllegalStateException.class, account::generateLoginToken);
        }
    }

    private interface SaveHandler {
        void save(Account account) throws Exception;
    }

    private static Datastore fakeDatastore(SaveHandler handler) {
        return (Datastore)
                Proxy.newProxyInstance(
                        Datastore.class.getClassLoader(),
                        new Class<?>[] {Datastore.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("save")
                                    && args != null
                                    && args.length > 0
                                    && args[0] instanceof Account account) {
                                handler.save(account);
                                return account;
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
    }

    private static AutoCloseable installDatastore(Datastore datastore) throws Exception {
        Field datastoreField = DatabaseManager.class.getDeclaredField("gameDatastore");
        Field runModeField = Grasscutter.class.getDeclaredField("runModeOverride");
        datastoreField.setAccessible(true);
        runModeField.setAccessible(true);

        Object previousDatastore = datastoreField.get(null);
        Object previousRunMode = runModeField.get(null);
        datastoreField.set(null, datastore);
        Grasscutter.setRunModeOverride(ServerRunMode.HYBRID);

        return () -> {
            datastoreField.set(null, previousDatastore);
            runModeField.set(null, previousRunMode);
        };
    }
}
