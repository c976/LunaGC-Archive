package emu.grasscutter.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests the credential matcher without booting the resource-dependent game server. */
public class AccountTokenTest {
    @Test
    public void missingCredentialsNeverAuthenticate() {
        assertFalse(Account.credentialMatches(null, null));
        assertFalse(Account.credentialMatches(null, "game-token"));
        assertFalse(Account.credentialMatches("game-token", null));
        assertFalse(Account.credentialMatches("", ""));
        assertFalse(Account.credentialMatches("", "game-token"));
    }

    @Test
    public void onlyTheMatchingCredentialAuthenticates() {
        assertTrue(Account.credentialMatches("game-token", "game-token"));
        assertTrue(Account.credentialMatches("v2_account-session", "v2_account-session"));
        assertFalse(Account.credentialMatches("game-token", "v2_account-session"));
        assertFalse(Account.credentialMatches("game-token", "wrong-token"));
        assertFalse(Account.credentialMatches("v2_account-session", "game-token"));
    }
}
