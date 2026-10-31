package emu.grasscutter.database;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

import emu.grasscutter.game.Account;
import java.util.Objects;
import org.bson.conversions.Bson;

/** Changes a password and revokes both token types in one synchronous MongoDB update. */
public final class AccountPasswordResetService {
    private AccountPasswordResetService() {}

    static Bson passwordResetUpdate(String passwordHash) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        return combine(
                set("password", passwordHash),
                unset("token"),
                unset("sessionKey"));
    }

    public static void resetPassword(Account account, String passwordHash) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(account.getId(), "account.id");
        Bson update = passwordResetUpdate(passwordHash);

        // Async account writes must finish before the password and token update. A single
        // MongoDB update makes the new password and the two token revocations atomic.
        try (DatabaseWriteBarrier ignored = DatabaseWriteBarrier.acquire()) {
            var accounts = DatabaseManager.getAccountDatastore().getDatabase().getCollection("accounts");
            var result = accounts.updateOne(eq("_id", account.getId()), update);
            if (result.getMatchedCount() != 1) {
                throw new IllegalStateException("Account no longer exists: " + account.getUsername());
            }
        }

        // Keep the caller's in-memory account consistent with the persisted credentials.
        account.setPassword(passwordHash);
        account.setToken(null);
        account.setSessionKey(null);
    }
}
