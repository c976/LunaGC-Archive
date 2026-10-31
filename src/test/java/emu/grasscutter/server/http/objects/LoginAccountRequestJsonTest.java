package emu.grasscutter.server.http.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the "account&&password" split.
 *
 * <p>Signing in with an unknown name registers it, so this split decides what gets created. A
 * username that fails to split must leave both fields null rather than falling back to the whole
 * string: the password box is not read on this path, so such a fallback would register - and then
 * sign in - on the strength of a username alone.
 */
public final class LoginAccountRequestJsonTest {
    private static LoginAccountRequestJson parsed(String usernameBox) {
        var request = new LoginAccountRequestJson();
        request.account = usernameBox;
        request.password = "ignored, the password box is not read on this path";
        request.parse();
        return request;
    }

    @Test
    @DisplayName("the two halves are split apart")
    public void splitsBothHalves() {
        var request = parsed("someone&&hunter2");

        assertEquals("someone", request.account);
        assertEquals("hunter2", request.password);
    }

    @Test
    @DisplayName("only the first separator splits, so a password may contain one")
    public void passwordMayContainTheSeparator() {
        var request = parsed("someone&&a&&b");

        assertEquals("someone", request.account);
        assertEquals("a&&b", request.password);
    }

    @Test
    @DisplayName("no separator clears both halves instead of trusting the username")
    public void noSeparatorClearsBoth() {
        var request = parsed("someone");

        assertNull(request.account);
        assertNull(request.password);
    }

    @Test
    @DisplayName("a null username box clears both halves")
    public void nullUsernameBox() {
        var request = parsed(null);

        assertNull(request.account);
        assertNull(request.password);
    }

    @Test
    @DisplayName("an empty half is preserved, for the caller to reject")
    public void emptyHalvesArePreserved() {
        // These are not silently accepted: the authenticator refuses an empty account name or an
        // empty password half before anything is registered.
        var emptyPassword = parsed("someone&&");
        assertEquals("someone", emptyPassword.account);
        assertEquals("", emptyPassword.password);

        var emptyAccount = parsed("&&hunter2");
        assertEquals("", emptyAccount.account);
        assertEquals("hunter2", emptyAccount.password);
    }
}
