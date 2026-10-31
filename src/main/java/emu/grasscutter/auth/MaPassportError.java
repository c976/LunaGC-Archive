package emu.grasscutter.auth;

/** Stable ma-passport business errors returned to the SDK client. */
public enum MaPassportError {
    INVALID_REQUEST(-1, "Invalid request"),
    MISSING_CREDENTIALS(-1, "Missing credentials"),
    CREDENTIAL_DECRYPTION_FAILED(-10, "Unable to decrypt credentials"),
    ACCOUNT_NOT_FOUND(-3203, "Account does not exist"),
    LOGIN_FAILED(-3208, "Account or password error"),
    RELOGIN_REQUIRED(-101, "For account safety, please log in again"),
    INTERNAL_ERROR(-1, "Internal server error");

    private final int retcode;
    private final String message;

    MaPassportError(int retcode, String message) {
        this.retcode = retcode;
        this.message = message;
    }

    public int retcode() {
        return retcode;
    }

    public String message() {
        return message;
    }
}
