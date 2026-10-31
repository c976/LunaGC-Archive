package emu.grasscutter.database;

import emu.grasscutter.game.Account;
import emu.grasscutter.game.AccountUsernamePolicy;

/** Insert-only account creation policy, separated so races can be tested without a live MongoDB. */
final class AccountCreationService {
    private static final int MAX_ID_RETRIES = 64;

    enum InsertResult {
        INSERTED,
        ID_TAKEN,
        USERNAME_TAKEN
    }

    interface Store {
        int nextId();

        InsertResult tryInsert(Account account);
    }

    private AccountCreationService() {}

    static Account insert(Account account, Store store) {
        AccountUsernamePolicy.requireValid(account.getUsername());
        for (int attempt = 0; attempt < MAX_ID_RETRIES; attempt++) {
            account.setId(Integer.toString(store.nextId()));
            switch (store.tryInsert(account)) {
                case INSERTED -> {
                    return account;
                }
                case USERNAME_TAKEN -> {
                    return null;
                }
                case ID_TAKEN -> {
                    // A stale pre-fix counter can still point at an existing _id. Reserve another.
                }
            }
        }
        throw new IllegalStateException("Could not reserve a free account id");
    }
}
