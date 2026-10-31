package emu.grasscutter.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.MongoClientSettings;
import emu.grasscutter.game.Account;
import org.bson.Document;
import org.junit.jupiter.api.Test;

public final class AccountPasswordResetServiceTest {
    @Test
    void passwordResetUpdateChangesPasswordAndRevokesBothTokenKinds() {
        var update =
                AccountPasswordResetService.passwordResetUpdate("hashed-password")
                        .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry());

        assertEquals("hashed-password", update.getDocument("$set").getString("password").getValue());
        assertEquals(1, update.getDocument("$set").size());
        assertTrue(update.getDocument("$unset").containsKey("token"));
        assertTrue(update.getDocument("$unset").containsKey("sessionKey"));
        assertEquals(2, update.getDocument("$unset").size());
    }

    @Test
    void revokedCredentialsCannotAuthenticateEvenWithEmptyToken() {
        var account = new Account();
        account.setToken("game-token");
        account.setSessionKey("session-token");
        assertTrue(account.matchesLoginToken("game-token"));
        assertTrue(account.matchesSessionKey("session-token"));

        account.setToken(null);
        account.setSessionKey(null);
        assertFalse(account.matchesLoginToken("game-token"));
        assertFalse(account.matchesSessionKey("session-token"));
        assertFalse(account.matchesLoginToken(""));
        assertFalse(account.matchesSessionKey(""));
    }
}
