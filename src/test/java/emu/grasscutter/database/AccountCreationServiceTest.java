package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.Account;
import java.util.Map;
import org.bson.Document;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AccountCreationServiceTest {
    @Test
    void newAccountNamesRejectUidAndIpSeparators() {
        for (String bad : java.util.Arrays.asList(null, "", "  ", "alice@example",
                "alice.name", "1.2.3.4", "@10001")) {
            assertFalse(emu.grasscutter.game.AccountUsernamePolicy.isValid(bad));
            assertThrows(IllegalArgumentException.class,
                    () -> AccountCreationService.insert(account(bad), new FakeStore(10000)));
        }
        for (String good : java.util.List.of("account_name", "alice", "name-123")) {
            assertTrue(emu.grasscutter.game.AccountUsernamePolicy.isValid(good));
        }
    }

    @Test
    void newlyInsertedAccountsReceiveNoImplicitSuperuserPermission() {
        var store = new FakeStore(10_000);
        var created = AccountCreationService.insert(account("ordinary"), store);

        assertNotNull(created);
        assertTrue(created.getPermissions().isEmpty());
        assertTrue(store.byId.get(created.getId()).getPermissions().isEmpty());
    }

    @Test
    void legacyDocumentsWithoutPermissionFieldDoNotBecomeAdministrators() {
        var loaded = account("legacy");
        loaded.onLoad(new Document("username", "legacy"));
        assertTrue(loaded.getPermissions().isEmpty());

        // Explicit administrative grants must still be preserved for existing admins.
        loaded.addPermission("*");
        loaded.onLoad(new Document("username", "legacy").append("permissions", java.util.List.of("*")));
        assertEquals(java.util.List.of("*"), loaded.getPermissions());
    }

    @Test
    void staleIdCollisionRetriesWithoutOverwritingExistingAccount() {
        var store = new FakeStore(10_000);
        var existing = account("existing");
        existing.setId("10001");
        store.byId.put(existing.getId(), existing);
        store.byName.put(existing.getUsername(), existing);

        var created = AccountCreationService.insert(account("new-user"), store);

        assertNotNull(created);
        assertEquals("10002", created.getId());
        assertSame(existing, store.byId.get("10001"));
        assertEquals("existing", store.byId.get("10001").getUsername());
    }

    @Test
    void concurrentDistinctAccountsReceiveDistinctIds() throws Exception {
        var store = new FakeStore(10_000);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return AccountCreationService.insert(account("one"), store);
                            });
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return AccountCreationService.insert(account("two"), store);
                            });
            start.countDown();
            var first = one.get();
            var second = two.get();
            assertNotNull(first);
            assertNotNull(second);
            assertNotEquals(first.getId(), second.getId());
        }
    }

    @Test
    void concurrentSameUsernameDoesNotReplaceWinner() throws Exception {
        var store = new FakeStore(10_000);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return AccountCreationService.insert(account("same"), store);
                            });
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return AccountCreationService.insert(account("same"), store);
                            });
            start.countDown();
            var first = one.get();
            var second = two.get();
            assertTrue((first == null) ^ (second == null));
            var winner = first != null ? first : second;
            assertSame(winner, store.byName.get("same"));
        }
    }

    private static Account account(String username) {
        var account = new Account();
        account.setUsername(username);
        return account;
    }

    private static final class FakeStore implements AccountCreationService.Store {
        private final AtomicInteger ids;
        private final Map<String, Account> byId = new ConcurrentHashMap<>();
        private final Map<String, Account> byName = new ConcurrentHashMap<>();

        private FakeStore(int first) {
            this.ids = new AtomicInteger(first);
        }

        @Override
        public int nextId() {
            return ids.incrementAndGet();
        }

        @Override
        public synchronized AccountCreationService.InsertResult tryInsert(Account account) {
            if (byName.containsKey(account.getUsername())) {
                return AccountCreationService.InsertResult.USERNAME_TAKEN;
            }
            if (byId.containsKey(account.getId())) {
                return AccountCreationService.InsertResult.ID_TAKEN;
            }
            byId.put(account.getId(), account);
            byName.put(account.getUsername(), account);
            return AccountCreationService.InsertResult.INSERTED;
        }
    }
}
