package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import io.restassured.response.Response;
import io.restassured.path.json.JsonPath;

import java.util.*;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.*;

public final class ResponseCheck {
    static void checkErrorCode(Response response) {
        Integer code = response.path("errorCode");
        if (code != null && Arrays.stream(ErrorCode.values()).noneMatch(error -> error.errorCode == code))
            fail("Ошибка сервера: errorCode %s: %s", code, response.asString());
    }

    /** Отсекает признаки отказа; структуру и обязательные поля проверяет схема. */
    public static JsonPath success(Response response) {
        var json = response.jsonPath();
        String resultCode = json.get("resultCode");
        if (json.get("errorCode") != null || Integer.valueOf(-1).equals(json.get("responseCode"))
                || (resultCode != null && !"SUCCESS".equals(resultCode))
                || Boolean.FALSE.equals(json.get("checkSuccess")) || Boolean.TRUE.equals(json.get("secureCheck.errorExist")))
            fail("Ответ содержит отказ: %s", response.asString());
        return json;
    }

    public static void error(Response response, ErrorCode expected) {
        assertThat(response.jsonPath().getMap("")).as("ошибка: %s", response.asString())
                .extractingByKeys("errorCode", "errorDesc").containsExactly(expected.errorCode, expected.errorDesc);
        matchesSchema(response, "schemas/errors/error.json");
    }

    public static void rejected(Response response, String badItem, String errorMessage) {
        assertThat(response.<List<Map<String, Object>>>path("checkResult")).as("checkResult: %s", response.asString())
                .extracting(check -> check.get("badItem"), check -> check.get("errorMessage"), check -> check.get("error"))
                .contains(tuple(badItem, errorMessage, true));
        matchesSchema(response, "schemas/errors/check_failed.json");
    }

    public static void matchesSchema(Response response, String schema) {
        Allure.step("Схема " + schema, () -> response.then().body(matchesJsonSchemaInClasspath(schema)));
    }
}
