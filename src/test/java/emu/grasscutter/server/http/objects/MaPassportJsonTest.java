package emu.grasscutter.server.http.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import emu.grasscutter.auth.MaPassportAuthenticator;
import emu.grasscutter.auth.MaPassportError;
import org.junit.jupiter.api.Test;

public class MaPassportJsonTest {
    @Test
    void loginErrorsKeepProtocolDataNull() {
        var response =
                MaPassportAuthenticator.createLoginErrorResponse(MaPassportError.ACCOUNT_NOT_FOUND);
        var json = JsonParser.parseString(MaPassportJson.encode(response)).getAsJsonObject();

        assertEquals(-3203, json.get("retcode").getAsInt());
        assertEquals("Account does not exist", json.get("message").getAsString());
        assertTrue(json.has("data"));
        assertTrue(json.get("data").isJsonNull());
    }

    @Test
    void passwordFailureUsesLoginFailureRetcode() {
        var response = MaPassportAuthenticator.createLoginErrorResponse(MaPassportError.LOGIN_FAILED);
        var json = JsonParser.parseString(MaPassportJson.encode(response)).getAsJsonObject();

        assertEquals(-3208, json.get("retcode").getAsInt());
        assertTrue(json.get("data").isJsonNull());
    }

    @Test
    void encoderOnlyRestoresTopLevelDataNull() {
        var response = new LoginByPasswordResponseJson();
        response.retcode = 0;
        response.message = "OK";
        response.data = new LoginByPasswordResponseJson.LoginData();

        var json = JsonParser.parseString(MaPassportJson.encode(response)).getAsJsonObject();

        assertTrue(json.has("data"));
        assertFalse(json.getAsJsonObject("data").has("token"));
        assertFalse(json.getAsJsonObject("data").has("user_info"));
    }
}
