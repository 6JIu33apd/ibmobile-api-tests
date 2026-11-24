package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.api.AuthMethod;
import ru.avangard.qa.ibmobile.api.DocType;
import ru.avangard.qa.ibmobile.config.Config;

import java.util.List;

import static io.restassured.RestAssured.given;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

/**
 * Подтверждает и отправляет документ способом, выбранным в тесте.
 */
public final class SendDoc {
    public static void send(DocType docType, AuthMethod method, Response prepared) {
        Response response = given().formParam("secure", switch (method) {
            case NONE -> List.of(secure(method.name(), prepared.path("noneAuthInit.code")));
            case SCRETCH -> {
                success(given().post(SHOW_SCRETCH.path()));
                yield List.of(secure(method.name(), Config.get("scretch")));
            }
            case SMSPWD -> {
                success(given().formParam("password", Config.get("password")).post(CHECK_PASSWORD.path()));
                success(given().post(SEND_CONFIRM_SMS.path()));
                yield List.of(secure("SMS", Config.get("sms")), secure("PWD", Config.get("password")));
            }
        }).post(SEND_DOC.path());
        matchesSchema(response, SEND_DOC.schema(docType));
    }

    private static String secure(String method, String value) {
        return JsonNodeFactory.instance.objectNode().put("authMethod", method).put("authToken", value).toString();
    }
}
