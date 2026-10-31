package emu.grasscutter.server.http.objects;

public class LoginAccountRequestJson {
    /** Carries "account&&password" when integration passwords are on. */
    public String account;

    public String password;
    public boolean is_crypto;

    /** What separates the two halves of the username box. */
    public static final String COMBINER = "&&";

    /**
     * Splits an {@code account&&password} username into its two halves.
     *
     * <p>A username that does not hold the separator leaves both fields null rather than falling
     * back to treating the whole string as the account name: the password box is not consulted on
     * this path, so such a fallback would log the player in on the strength of a username alone.
     *
     * <p>Only the first separator splits, so a password may itself contain "&&".
     */
    public void parse() {
        if (this.account != null) {
            var parts = this.account.split(COMBINER, 2);
            if (parts.length == 2) {
                this.account = parts[0];
                this.password = parts[1];
                return;
            }
        }

        this.account = null;
        this.password = null;
    }
}
