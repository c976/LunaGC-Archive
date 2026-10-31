package emu.grasscutter.server.http.console;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import emu.grasscutter.config.ConfigContainer.WebConsole;
import org.junit.jupiter.api.Test;

class WebConsoleAuthTest {
    private static WebConsoleAuth auth(String password) {
        var options = new WebConsole();
        options.enabled = true;
        options.password = password;
        return new WebConsoleAuth(options);
    }

    @Test
    void rightPasswordGivesAWorkingToken() {
        var auth = auth("secret");
        var token = auth.login("1.2.3.4", "secret");
        assertNotNull(token);
        assertTrue(auth.isValid(token));

        auth.logout(token);
        assertFalse(auth.isValid(token));
    }

    @Test
    void wrongOrMissingPasswordIsRefused() {
        var auth = auth("secret");
        assertNull(auth.login("1.2.3.4", "Secret"));
        assertNull(auth.login("1.2.3.4", null));
        assertFalse(auth.isValid(null));
        assertFalse(auth.isValid("made-up"));
    }

    @Test
    void unsetOrBlankPasswordNeverAuthenticates() {
        for (var configured : new String[] {null, "", "  "}) {
            var auth = auth(configured);
            assertNull(auth.login("1.2.3.4", ""));
            assertNull(auth.login("1.2.3.4", "mechen618"));
            assertNull(auth.login("1.2.3.4", null));
        }
    }

    @Test
    void consoleIsDisabledAndHasNoCredentialByDefault() {
        var options = new WebConsole();
        assertFalse(options.enabled);
        assertNull(options.effectivePassword());

        var json = emu.grasscutter.utils.JsonUtils.encode(options);
        assertFalse(json.contains("password"), json);
        assertTrue(json.contains("enabled"), json);
    }

    @Test
    void disabledConsoleRefusesEvenAnExplicitPassword() {
        var options = new WebConsole();
        options.password = "secret";
        var auth = new WebConsoleAuth(options);
        assertNull(auth.login("1.2.3.4", "secret"));
    }

    @Test
    void configuredPasswordIsTheOnlyAcceptedCredential() {
        var auth = auth("secret");
        assertNull(auth.login("1.2.3.4", "mechen618"));
        assertNotNull(auth.login("1.2.3.4", "secret"));
    }

    @Test
    void fiveWrongPasswordsLockOnlyThatAddress() {
        var auth = auth("secret");
        for (int i = 0; i < 5; i++) assertNull(auth.login("1.2.3.4", "wrong"));
        assertTrue(auth.isLockedOut("1.2.3.4"));
        assertFalse(auth.isLockedOut("5.6.7.8"));
    }

    @Test
    void expiredSessionIsRejected() {
        var options = new WebConsole();
        options.enabled = true;
        options.password = "secret";
        options.sessionHours = 0;
        var auth = new WebConsoleAuth(options);
        var token = auth.login("1.2.3.4", "secret");
        // A zero-hour session expires as soon as the clock moves past the login instant.
        long until = System.currentTimeMillis() + 5;
        while (System.currentTimeMillis() <= until) Thread.onSpinWait();
        assertFalse(auth.isValid(token));
    }
}
