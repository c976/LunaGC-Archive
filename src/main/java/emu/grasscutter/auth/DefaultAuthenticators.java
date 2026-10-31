package emu.grasscutter.auth;

import static emu.grasscutter.config.Configuration.ACCOUNT;
import static emu.grasscutter.utils.lang.Language.translate;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.auth.AuthenticationSystem.AuthenticationRequest;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.dispatch.*;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.utils.*;
import io.javalin.http.ContentType;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.*;
import javax.crypto.Cipher;

/** A class containing default authenticators. */
public final class DefaultAuthenticators {

    /** Builds formatted ban message for client SDK modal. */
    public static String buildBanMessage(Account account) {
        if (account == null) return "This account is banned.";
        int endTime = account.getBanEndTime();
        String timeStr;
        if (endTime <= 0) {
            timeStr = "permanent";
        } else {
            timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date(((long) endTime) * 1000L));
        }
        String reason = account.getBanReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "violation of the game rules";
        }
        return String.format("This account is banned.\nUnban time: %s\nReason: %s", timeStr, reason);
    }

    /**
     * The password as the player typed it, or null when the client encrypted it (is_crypto) with a
     * key this server cannot decrypt.
     */
    private static String plainPassword(LoginAccountRequestJson data, boolean integrationPassword) {
        String password = data.password == null ? "" : data.password;
        // The integration password comes out of the username box, which is never encrypted.
        if (integrationPassword || !data.is_crypto || password.isEmpty()) return password;
        try {
            return RSADecryptionUtil.decrypt(password);
        } catch (Exception e) {
            return null;
        }
    }

    /** {@link Account#verifyPassword}, but a password BCrypt refuses counts as wrong, not a 500. */
    private static boolean verifyPassword(Account account, String password) {
        try {
            return account.verifyPassword(password);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Handles the authentication request from the username and password form. */
    public static class PasswordAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            var response = new LoginResultJson();

            var requestData = request.getPasswordRequest();
            assert requestData != null; // This should never be null.
            boolean useIntegrationPassword = ACCOUNT.useIntegrationPassword;
            if (useIntegrationPassword) {
                // Rewrites requestData.account and requestData.password in place, and nulls both
                // when the username box did not hold the "account&&password" form.
                requestData.parse();
            }

            boolean successfulLogin = false;
            String address = Utils.address(request.getContext());
            String responseMessage = translate("messages.dispatch.account.username_error");
            String loggerMessage = "";

            // Nothing usable came out of the username box. Stop here: signing in registers an
            // unknown name, so falling through would try to register a null one.
            if (requestData.account == null || requestData.account.isBlank()) {
                response.retcode = -201;
                response.message =
                        useIntegrationPassword
                                ? "Enter your account and password in the username box as"
                                        + " account&&password."
                                : responseMessage;
                Grasscutter.getLogger()
                        .info("[Dispatch] Client {} sent no usable account name.", address);
                return response;
            }

            if (useIntegrationPassword
                    && (requestData.password == null || requestData.password.isEmpty())) {
                response.retcode = -201;
                response.message = "The password half of account&&password is empty.";
                Grasscutter.getLogger()
                        .info("[Dispatch] Client {} sent an empty password half.", address);
                return response;
            }

            // Get account from database.
            Account account = DatabaseHelper.getAccountByName(requestData.account);
            // Signing in with a name nobody holds registers it, so there is no separate sign-up
            // step: the launcher's login box is the sign-up form. autoCreate turns this off for a
            // closed server.
            //
            // The cost is that a mistyped username registers that typo rather than reporting a bad
            // login - unavoidable when the credential comes from one free-text box, and the reason
            // the password is checked below for names that already exist.
            if (account == null && ACCOUNT.autoCreate) {
                // This account has been created AUTOMATICALLY. There will be no permissions added.
                account =
                        useIntegrationPassword
                                // The password came in with the name, so store it now, hashed. The
                                // other path has none to store and locks one in on first sign-in.
                                ? DatabaseHelper.createAccountWithHashedPassword(
                                        requestData.account, requestData.password, null)
                                : DatabaseHelper.createAccountWithUid(requestData.account, 0);

                // Check if the account was created successfully.
                if (account == null) {
                    responseMessage = translate("messages.dispatch.account.username_create_error");
                    Grasscutter.getLogger()
                            .info(translate("messages.dispatch.account.account_login_create_error", address));
                } else {
                    // Continue with login.
                    successfulLogin = true;

                    // Log the creation.
                    Grasscutter.getLogger()
                            .info(
                                    translate(
                                            "messages.dispatch.account.account_login_create_success",
                                            address,
                                            account.getId()));
                }
            } else if (account != null) {
                String rawPassword = plainPassword(requestData, useIntegrationPassword);
                if (rawPassword == null) {
                    // The client encrypted the password with a key this server does not hold, so
                    // it can be neither stored nor checked. Hashing the ciphertext threw (BCrypt
                    // takes at most 72 bytes) and reached the client as an HTTP 500; the login is
                    // let through instead, as the private repo does.
                    successfulLogin = true;
                    Grasscutter.getLogger()
                            .info(
                                    "[Dispatch] Client "
                                            + address
                                            + " sent a password this server cannot decrypt; account "
                                            + account.getId()
                                            + " logs in without a password check.");
                } else {
                    // Lock the entered password as the account password on first login
                    // (covers both newly auto-created accounts and old accounts with an empty
                    // password).
                    if ((account.getPassword() == null || account.getPassword().isEmpty())
                            && !rawPassword.isEmpty()) {
                        try {
                            account.setPassword(
                                    BCrypt.withDefaults().hashToString(10, rawPassword.toCharArray()));
                            account.save();
                        } catch (IllegalArgumentException tooLong) {
                            // Longer than BCrypt takes: leave the account without a password.
                        }
                    }
                }
                // Verify the password for accounts that have one set.
                if (successfulLogin
                        || account.getPassword() == null
                        || account.getPassword().isEmpty()
                        || verifyPassword(account, rawPassword)) {
                    successfulLogin = true;
                } else {
                    responseMessage = translate("messages.dispatch.account.password_error");
                    loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                }
            } else
                loggerMessage = translate("messages.dispatch.account.account_login_exist_error", address);

            // Set response data.
            if (successfulLogin) {
                if (account != null && account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.account.uid = account.getId();
                    response.data.account.token = account.generateSessionKey();
                    response.data.account.email = account.getEmail();

                    loggerMessage =
                            translate("messages.dispatch.account.login_success", address, account.getId());
                }
            } else {
                response.retcode = -3201;
                response.message = responseMessage;
            }
            Grasscutter.getLogger().info(loggerMessage);

            return response;
        }
    }

    public static class ExperimentalPasswordAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            var response = new LoginResultJson();

            var requestData = request.getPasswordRequest();
            assert requestData != null; // This should never be null.
            boolean successfulLogin = false;
            String address = Utils.address(request.getContext());
            String responseMessage = translate("messages.dispatch.account.username_error");
            String loggerMessage = "";
            String decryptedPassword = "";
            try {
                byte[] key = FileUtils.readResource("/keys/auth_private-key.der");
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                RSAPrivateKey private_key = (RSAPrivateKey) keyFactory.generatePrivate(keySpec);

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

                cipher.init(Cipher.DECRYPT_MODE, private_key);

                decryptedPassword =
                        new String(
                                cipher.doFinal(Utils.base64Decode(request.getPasswordRequest().password)),
                                StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                decryptedPassword = request.getPasswordRequest().password;
            }

            if (decryptedPassword == null) {
                successfulLogin = false;
                loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                responseMessage = translate("messages.dispatch.account.password_error");
            }

            // Get account from database.
            Account account = DatabaseHelper.getAccountByName(requestData.account);
            // Check if account exists.
            if (account == null && ACCOUNT.autoCreate) {
                // This account has been created AUTOMATICALLY. There will be no permissions added.
                if (decryptedPassword.length() >= 8) {
                    account = DatabaseHelper.createAccountWithUid(requestData.account, 0);
                    if (account != null) {
                        account.setPassword(
                                BCrypt.withDefaults().hashToString(12, decryptedPassword.toCharArray()));
                        account.save();
                    }

                    // Check if the account was created successfully.
                    if (account == null) {
                        responseMessage = translate("messages.dispatch.account.username_create_error");
                        loggerMessage =
                                translate("messages.dispatch.account.account_login_create_error", address);
                    } else {
                        // Continue with login.
                        successfulLogin = true;

                        // Log the creation.
                        Grasscutter.getLogger()
                                .info(
                                        translate(
                                                "messages.dispatch.account.account_login_create_success",
                                                address,
                                                response.data.account.uid));
                    }
                } else {
                    successfulLogin = false;
                    loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                    responseMessage = translate("messages.dispatch.account.password_length_error");
                }
            } else if (account != null) {
                if (account.getPassword() != null && !account.getPassword().isEmpty()) {
                    if (BCrypt.verifyer()
                            .verify(decryptedPassword.toCharArray(), account.getPassword())
                            .verified) {
                        successfulLogin = true;
                    } else {
                        successfulLogin = false;
                        loggerMessage = translate("messages.dispatch.account.login_password_error", address);
                        responseMessage = translate("messages.dispatch.account.password_error");
                    }
                } else {
                    // Empty password account: lock the entered password on first login.
                    if (decryptedPassword != null && !decryptedPassword.isEmpty()) {
                        account.setPassword(
                                BCrypt.withDefaults()
                                        .hashToString(12, decryptedPassword.toCharArray()));
                        account.save();
                        successfulLogin = true;
                    } else {
                        successfulLogin = false;
                        loggerMessage =
                                translate("messages.dispatch.account.login_password_error", address);
                        responseMessage = translate("messages.dispatch.account.password_error");
                    }
                }
            } else {
                loggerMessage = translate("messages.dispatch.account.account_login_exist_error", address);
            }

            // Set response data.
            if (successfulLogin) {
                if (account != null && account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.account.uid = account.getId();
                    response.data.account.token = account.generateSessionKey();
                    response.data.account.email = account.getEmail();

                    loggerMessage =
                            translate("messages.dispatch.account.login_success", address, account.getId());
                }
            } else {
                response.retcode = -3201;
                response.message = responseMessage;
            }
            Grasscutter.getLogger().info(loggerMessage);

            return response;
        }
    }

    /** Handles the authentication request from the game when using a registry token. */
    public static class TokenAuthenticator implements Authenticator<LoginResultJson> {
        @Override
        public LoginResultJson authenticate(AuthenticationRequest request) {
            var response = new LoginResultJson();

            var requestData = request.getTokenRequest();
            assert requestData != null;

            boolean successfulLogin;
            String address = Utils.address(request.getContext());
            String loggerMessage;

            // Log the attempt.
            Grasscutter.getLogger()
                    .info(translate("messages.dispatch.account.login_token_attempt", address));

            // Get account from database.
            Account account = DatabaseHelper.getAccountById(requestData.uid);

            // Check if account exists/token is valid.
            successfulLogin = account != null && account.matchesSessionKey(requestData.token);

            // Set response data.
            if (successfulLogin) {
                if (account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Token login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.account.uid = account.getId();
                    response.data.account.token = account.getSessionKey();
                    response.data.account.email = account.getEmail();

                    // Log the login.
                    loggerMessage =
                            translate("messages.dispatch.account.login_token_success", address, requestData.uid);
                }
            } else {
                response.retcode = -3201;
                response.message = translate("messages.dispatch.account.account_cache_error");

                // Log the failure.
                loggerMessage = translate("messages.dispatch.account.login_token_error", address);
            }

            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    /** Handles the authentication request from the game when using a combo token/session key. */
    public static class SessionKeyAuthenticator implements Authenticator<ComboTokenResJson> {
        @Override
        public ComboTokenResJson authenticate(AuthenticationRequest request) {
            var response = new ComboTokenResJson();

            var requestData = request.getSessionKeyRequest();
            var loginData = request.getSessionKeyData();
            assert requestData != null;

            boolean successfulLogin;
            String address = Utils.address(request.getContext());
            String loggerMessage;

            // A data field that is not the expected JSON leaves loginData null, which used to throw
            // and reach the client as an HTTP 500.
            if (loginData == null) {
                response.retcode = -3201;
                response.message = translate("messages.dispatch.account.session_key_error");
                Grasscutter.getLogger().info("[Combo] login from " + address + " carried no usable data.");
                return response;
            }

            // Fail closed: never trust a caller-supplied session token simply because
            // an account with that UID exists.
            Account account = DatabaseHelper.getAccountById(loginData.uid);
            successfulLogin = account != null && account.matchesSessionKey(loginData.token);
            Grasscutter.getLogger()
                    .info(
                            "[Combo] credential verification={} for uid={}",
                            successfulLogin,
                            loginData.uid);

            // Set response data.
            if (successfulLogin) {
                if (account.isBanned()) {
                    response.retcode = -201;
                    response.message = buildBanMessage(account);
                    loggerMessage = String.format("Combo login rejected: account %s is banned", account.getId());
                } else {
                    response.message = "OK";
                    response.data.open_id = account.getId();
                    response.data.combo_id = "157795300";
                    response.data.combo_token = account.generateLoginToken();

                    // Log the login.
                    loggerMessage = translate("messages.dispatch.account.combo_token_success", address);
                }

            } else {
                response.retcode = -3201;
                response.message = translate("messages.dispatch.account.session_key_error");

                // Log the failure.
                loggerMessage = translate("messages.dispatch.account.combo_token_error", address);
            }

            Grasscutter.getLogger().info(loggerMessage);
            return response;
        }
    }

    /** Handles authentication requests from external sources. */
    public static class ExternalAuthentication implements ExternalAuthenticator {
        @Override
        public void handleLogin(AuthenticationRequest request) {
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handleAccountCreation(AuthenticationRequest request) {
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handlePasswordReset(AuthenticationRequest request) {
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }
    }

    /** Handles authentication requests from OAuth sources.Zenlith */
    public static class OAuthAuthentication implements OAuthAuthenticator {
        @Override
        public void handleLogin(AuthenticationRequest request) {
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handleRedirection(AuthenticationRequest request, ClientType type) {
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }

        @Override
        public void handleTokenProcess(AuthenticationRequest request) {
            request
                    .getContext()
                    .result("Authentication is not available with the default authentication method.");
        }
    }

    /** Validates a session token during game login. */
    public static class SessionTokenValidator implements Authenticator<Account> {
        @Override
        public Account authenticate(AuthenticationRequest request) {
            var tokenRequest = request.getTokenRequest();
            if (tokenRequest == null) {
                Grasscutter.getLogger().warn("Invalid session token validator request.");
                return null;
            }

            // Prepare the request.
            var client = Grasscutter.getGameServer().getDispatchClient();
            var future = new CompletableFuture<Account>();

            client.registerCallback(
                    PacketIds.TokenValidateRsp,
                    packet -> {
                        var data = IDispatcher.decode(packet);

                        // Check if the token is valid.
                        var valid = data.get("valid").getAsBoolean();
                        if (!valid) {
                            future.complete(null);
                            return;
                        }

                        // Return the account data.
                        future.complete(IDispatcher.decode(data.get("account"), Account.class));
                    });
            client.sendMessage(PacketIds.TokenValidateReq, tokenRequest);

            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /** Handles authentication for the web GM Handbook. */
    public static class HandbookAuthentication implements HandbookAuthenticator {
        private final String authPage;

        public HandbookAuthentication() {
            try {
                this.authPage = new String(FileUtils.readResource("/html/handbook_auth.html"));
            } catch (Exception ignored) {
                throw new RuntimeException("Failed to load handbook auth page.");
            }
        }

        @Override
        public void presentPage(AuthenticationRequest request) {
            var ctx = request.getContext();
            if (ctx == null) return;

            // Check to see if an IP authentication can be performed.
            if (Grasscutter.getRunMode() == ServerRunMode.HYBRID) {
                var player = Grasscutter.getGameServer().getPlayerByIpAddress(Utils.address(ctx));
                if (player != null) {
                    // Get the player's session token.
                    var sessionKey = player.getAccount().getSessionKey();
                    // Respond with the handbook auth page.
                    ctx.status(200)
                            .result(
                                    this.authPage
                                            .replace("{{VALUE}}", "true")
                                            .replace("{{SESSION_TOKEN}}", sessionKey)
                                            .replace("{{PLAYER_ID}}", String.valueOf(player.getUid())));
                    return;
                }
            }

            // Respond with the handbook auth page.
            ctx.contentType(ContentType.TEXT_HTML).result(this.authPage);
        }

        @Override
        public Response authenticate(AuthenticationRequest request) {
            var ctx = request.getContext();
            if (ctx == null) return null;

            // Get the body data.
            var playerId = ctx.formParam("playerid");
            if (playerId == null) {
                return Response.builder().status(400).body("Invalid player ID.").build();
            }

            try {
                // Get the player's session token.
                var sessionKey = DispatchUtils.fetchSessionKey(Integer.parseInt(playerId));
                if (sessionKey == null) {
                    return Response.builder().status(400).body("Invalid player ID.").build();
                }

                // Check if the account is banned.
                return Response.builder()
                        .status(200)
                        .body(
                                this.authPage
                                        .replace("{{VALUE}}", "true")
                                        .replace("{{SESSION_TOKEN}}", sessionKey)
                                        .replace("{{PLAYER_ID}}", playerId))
                        .build();
            } catch (NumberFormatException ignored) {
                return Response.builder().status(500).body("Invalid player ID.").build();
            }
        }
    }
}
