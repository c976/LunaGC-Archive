package emu.grasscutter.game;

/** Reserved separators for new account names. Existing stored accounts remain readable. */
public final class AccountUsernamePolicy {
    private AccountUsernamePolicy() {}

    public static boolean isValid(String username) {
        return username != null
                && !username.isBlank()
                && username.indexOf('@') < 0
                && username.indexOf('.') < 0;
    }

    public static void requireValid(String username) {
        if (!isValid(username)) {
            throw new IllegalArgumentException(
                    "Invalid account username: names must not be blank or contain '@' or '.'.");
        }
    }
}
