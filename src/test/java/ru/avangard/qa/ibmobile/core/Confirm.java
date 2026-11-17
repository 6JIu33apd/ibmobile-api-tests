package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.config.Config;

import java.util.List;

import static io.restassured.RestAssured.given;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.success;

/**
 * Подтверждает и отправляет документ способом, выбранным в тесте.
 */
public final class Confirm {
    private Confirm() {
    }

    public static Response send(String method, Response prepared) {
        List<String> secure;
        if (method.equals("SMSPWD")) {
            String password = Config.get("password");
            success(given().formParam("password", password).post(CHECK_PASSWORD.path()));
            success(given().post(SEND_CONFIRM_SMS.path()));
            secure = List.of(token("SMS", Config.get("sms")), token("PWD", password));
        } else {
            if (method.equals("SCRETCH")) success(given().post(SHOW_SCRETCH.path()));
            secure = List.of(token(method, method.equals("NONE") ? prepared.path("noneAuthInit.code") : Config.get("scretch")));
        }
        return given().formParam("secure", secure).post(SEND_DOC.path());
    }

    private static String token(String method, String value) {
        return JsonNodeFactory.instance.objectNode().put("authMethod", method).put("authToken", value).toString();
    }
}
