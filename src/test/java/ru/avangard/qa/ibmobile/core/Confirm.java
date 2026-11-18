package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.config.Config;

import java.util.List;

import static io.restassured.RestAssured.given;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.success;

/** Подтверждает и отправляет документ способом, выбранным в тесте. */
public final class Confirm {
    public static Response send(String method, Response prepared) {
        return given().formParam("secure", switch (method) {
            case "NONE" -> List.of(secureString(method, prepared.path("noneAuthInit.code")));
            case "SCRETCH" -> {
                success(given().post(SHOW_SCRETCH.path()));
                yield List.of(secureString(method, Config.get("scretch")));
            }
            case "SMSPWD" -> {
                String password = Config.get("password");
                success(given().formParam("password", password).post(CHECK_PASSWORD.path()));
                success(given().post(SEND_CONFIRM_SMS.path()));
                yield List.of(secureString("SMS", Config.get("sms")), secureString("PWD", password));
            }
            default -> throw new IllegalArgumentException(method);
        }).post(SEND_DOC.path());
    }

    private static String secureString(String method, String value) {
        return JsonNodeFactory.instance.objectNode().put("authMethod", method).put("authToken", value).toString();
    }
}
