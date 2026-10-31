package emu.grasscutter.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.Account;
import emu.grasscutter.server.http.objects.*;
import emu.grasscutter.utils.RSADecryptionUtil;
import java.util.ArrayList;

public class MaPassportAuthenticator {
    private static String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(10, password.toCharArray());
    }

    public static LoginByPasswordResponseJson appLoginByPassword(LoginByPasswordRequestJson request) {
        Grasscutter.getLogger().debug("ma-passport login req detected");
        if (request == null) {
            Grasscutter.getLogger().warn("Ma-passport login request is null");
            return createLoginErrorResponse(MaPassportError.INVALID_REQUEST);
        }
        if (request.account == null || request.password == null) {
            Grasscutter.getLogger().warn("Ma-passport login request is missing credentials");
            return createLoginErrorResponse(MaPassportError.MISSING_CREDENTIALS);
        }

        try {
            String username;
            try {
                username = RSADecryptionUtil.decrypt(request.account);
            } catch (Exception e) {
                Grasscutter.getLogger().warn("Unable to decrypt ma-passport account", e);
                return createLoginErrorResponse(MaPassportError.CREDENTIAL_DECRYPTION_FAILED);
            }

            String password;
            try {
                password = RSADecryptionUtil.decrypt(request.password);
            } catch (Exception e) {
                Grasscutter.getLogger().warn("Unable to decrypt ma-passport password", e);
                return createLoginErrorResponse(MaPassportError.CREDENTIAL_DECRYPTION_FAILED);
            }

            Account account = DatabaseHelper.getAccountByName(username);
            if (account == null && emu.grasscutter.config.Configuration.ACCOUNT.autoCreate) {
                account = DatabaseHelper.createAccountWithUid(username, 0);
                Grasscutter.getLogger().info("Auto-created account for: " + username);
            }
            if (account == null) {
                Grasscutter.getLogger().info("Account not found: " + username);
                return createLoginErrorResponse(MaPassportError.ACCOUNT_NOT_FOUND);
            }

            if ((account.getPassword() == null || account.getPassword().isEmpty())
                    && password != null
                    && !password.isEmpty()) {
                account.setPassword(hashPassword(password));
                account.save();
                Grasscutter.getLogger().info("Password locked for account: " + username);
            }

            if (!account.verifyPassword(password)) {
                Grasscutter.getLogger().info("Password verification failed for: " + username);
                return createLoginErrorResponse(MaPassportError.LOGIN_FAILED);
            }

            Grasscutter.getLogger().debug("Generating session key");
            account.generateV2SessionKey();
            Grasscutter.getLogger().info("User " + username + " has successfully logged in");
            return createLoginSuccessResponse(account);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in ma-passport password login", e);
            return createLoginErrorResponse(MaPassportError.INTERNAL_ERROR);
        }
    }

    public static VerifySTokenResponseJson verifySToken(VerifySTokenRequestJson request) {
        if (request == null) {
            Grasscutter.getLogger().warn("Ma-passport token verify request is null");
            return createTokenErrorResponse(MaPassportError.INVALID_REQUEST);
        }

        try {
            Grasscutter.getLogger().debug("Ma-passport token verification for mid: " + request.mid);
            Account account = DatabaseHelper.getAccountById(request.mid);
            if (account == null) {
                Grasscutter.getLogger().info("Account not found for mid: " + request.mid);
                return createTokenErrorResponse(MaPassportError.RELOGIN_REQUIRED);
            }

            if (!account.matchesSessionKey(request.stoken)) {
                Grasscutter.getLogger()
                        .info("Ma-passport token verification rejected for mid: {}", request.mid);
                return createTokenErrorResponse(MaPassportError.RELOGIN_REQUIRED);
            }

            Grasscutter.getLogger()
                    .debug("Ma-Passport token verification successful for: " + account.getUsername());
            return createTokenSuccessResponse(account);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error in ma-passport token verification", e);
            return createTokenErrorResponse(MaPassportError.INTERNAL_ERROR);
        }
    }

    private static LoginByPasswordResponseJson createLoginSuccessResponse(Account account) {
        LoginByPasswordResponseJson response = new LoginByPasswordResponseJson();
        response.retcode = 0;
        response.message = "OK";
        response.data = new LoginByPasswordResponseJson.LoginData();
        response.data.token = new LoginByPasswordResponseJson.TokenData();
        response.data.token.token_type = 1;
        response.data.token.token = account.getSessionKey();
        response.data.user_info = new LoginByPasswordResponseJson.UserInfoData();
        response.data.user_info.aid = account.getId();
        response.data.user_info.mid = account.getId();
        response.data.user_info.account_name = "";
        response.data.user_info.email = account.getUsername();
        response.data.user_info.is_email_verify = 0;
        response.data.user_info.area_code = "**";
        response.data.user_info.mobile = "";
        response.data.user_info.safe_area_code = "";
        response.data.user_info.safe_mobile = "";
        response.data.user_info.realname = "";
        response.data.user_info.identity_code = "";
        response.data.user_info.rebind_area_code = "";
        response.data.user_info.rebind_mobile = "";
        response.data.user_info.rebind_mobile_time = "315532800";
        response.data.user_info.links = new ArrayList<>();
        response.data.user_info.country = "US";
        response.data.user_info.password_time = "1762297200";
        response.data.user_info.is_adult = 1;
        response.data.user_info.is_email_verify = 1;
        response.data.user_info.password_time = "1762297200";
        response.data.user_info.unmasked_email = "";
        response.data.user_info.unmasked_email_type = 0;
        response.data.ext_user_info = new LoginByPasswordResponseJson.ExtUserInfoData();
        response.data.ext_user_info.guardian_email = "";
        response.data.ext_user_info.birth = "0";
        response.data.reactivate_action_ticket = "";
        response.data.bind_email_action_ticket = "";
        return response;
    }

    public static LoginByPasswordResponseJson createLoginErrorResponse(MaPassportError error) {
        LoginByPasswordResponseJson response = new LoginByPasswordResponseJson();
        response.retcode = error.retcode();
        response.message = error.message();
        response.data = null;
        return response;
    }

    private static VerifySTokenResponseJson createTokenSuccessResponse(Account account) {
        VerifySTokenResponseJson response = new VerifySTokenResponseJson();
        response.retcode = 0;
        response.message = "OK";
        response.data = new VerifySTokenResponseJson.VerifyData();
        response.data.user_info = new VerifySTokenResponseJson.UserInfoData();
        response.data.user_info.aid = account.getId();
        response.data.user_info.mid = account.getId();
        response.data.user_info.account_name = "";
        response.data.user_info.email = account.getUsername();
        response.data.user_info.is_email_verify = 0;
        response.data.user_info.area_code = "**";
        response.data.user_info.mobile = "";
        response.data.user_info.safe_area_code = "";
        response.data.user_info.safe_mobile = "";
        response.data.user_info.realname = "";
        response.data.user_info.identity_code = "";
        response.data.user_info.rebind_area_code = "";
        response.data.user_info.rebind_mobile = "";
        response.data.user_info.rebind_mobile_time = "315532800";
        response.data.user_info.links = new ArrayList<>();
        response.data.user_info.country = "US";
        response.data.user_info.password_time = "1762297200";
        response.data.user_info.is_adult = 1;
        response.data.user_info.is_email_verify = 1;
        response.data.user_info.unmasked_email = "";
        response.data.user_info.unmasked_email_type = 0;
        response.data.tokens = new ArrayList<>();
        VerifySTokenResponseJson.TokenData tokenData = new VerifySTokenResponseJson.TokenData();
        tokenData.token_type = 1;
        tokenData.token = account.getSessionKey();
        response.data.tokens.add(tokenData);
        response.data.ext_user_info = new VerifySTokenResponseJson.ExtUserInfoData();
        response.data.ext_user_info.guardian_email = "";
        response.data.ext_user_info.birth = "0";
        return response;
    }

    public static VerifySTokenResponseJson createTokenErrorResponse(MaPassportError error) {
        VerifySTokenResponseJson response = new VerifySTokenResponseJson();
        response.retcode = error.retcode();
        response.message = error.message();
        response.data = null;
        return response;
    }
}
