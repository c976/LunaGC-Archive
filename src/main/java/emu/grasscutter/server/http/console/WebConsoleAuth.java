package emu.grasscutter.server.http.console;

import emu.grasscutter.config.ConfigContainer.WebConsole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Password check, session tokens, and a lockout for repeated wrong passwords from one address. */
final class WebConsoleAuth {
    private static final int MAX_FAILURES = 5;
    private static final long LOCKOUT_MILLIS = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Failures(int count, long since) {}

    private final WebConsole options;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    WebConsoleAuth(WebConsole options) {
        this.options = options;
    }

    static String randomToken(int bytes) {
        var buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    boolean isLockedOut(String address) {
        var entry = failures.get(address);
        if (entry == null) return false;
        if (System.currentTimeMillis() - entry.since() > LOCKOUT_MILLIS) {
            failures.remove(address);
            return false;
        }
        return entry.count() >= MAX_FAILURES;
    }

    /** Returns a new session token, or null when the password is wrong. */
    String login(String address, String password) {
        var configuredPassword = options.effectivePassword();
        if (!options.enabled || configuredPassword == null) return null;

        var expected = configuredPassword.getBytes(StandardCharsets.UTF_8);
        var given = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || !MessageDigest.isEqual(expected, given)) {
            failures.merge(
                    address,
                    new Failures(1, System.currentTimeMillis()),
                    (old, one) -> new Failures(old.count() + 1, old.since()));
            return null;
        }

        failures.remove(address);
        var token = randomToken(32);
        sessions.put(token, System.currentTimeMillis() + options.sessionHours * 3_600_000L);
        return token;
    }

    boolean isValid(String token) {
        if (!options.enabled || options.effectivePassword() == null || token == null) return false;
        var expiry = sessions.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    void logout(String token) {
        if (token != null) sessions.remove(token);
    }
}
