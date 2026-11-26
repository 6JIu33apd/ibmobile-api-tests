package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import io.restassured.response.Response;
import io.restassured.path.json.JsonPath;

import java.util.*;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.*;

public final class ResponseCheck {
    /**
     * Отсекает признаки отказа; структуру и обязательные поля проверяет схема.
     */
    public static boolean success(Response response) {
        var json = response.jsonPath();
        if (json.get("errorCode") != null
                || Integer.valueOf(-1).equals(json.get("responseCode"))
                || (json.get("resultCode") != null && !"SUCCESS".equals(json.get("resultCode")))
                || Boolean.FALSE.equals(json.get("checkSuccess"))
                || Boolean.TRUE.equals(json.get("secureCheck.errorExist"))) {
            fail("Ответ содержит отказ: %s", response.asString());
            return false;
        }
        return true;
    }

    public static void error(Response response, ErrorCode expected) {
        Allure.step("Проверки", () -> {
            Allure.step("Код и описание ошибки", () -> assertThat(response.jsonPath().getMap("")).as("ошибка: %s", response.asString())
                    .extractingByKeys("errorCode", "errorDesc").containsExactly(expected.errorCode, expected.errorDesc));
            matchesSchema(response, "schemas/errors/error.json");
        });
    }

    public static void rejected(Response response, String badItem, String errorMessage) {
        Allure.step("Проверки", () -> {
            Allure.step("Причина отказа", () -> assertThat(response.<List<Map<String, Object>>>path("checkResult")).as("checkResult: %s", response.asString())
                    .extracting(check -> check.get("badItem"), check -> check.get("errorMessage"), check -> check.get("error"))
                    .contains(tuple(badItem, errorMessage, true)));
            matchesSchema(response, "schemas/errors/check_failed.json");
        });
    }

    public static void matchesSchema(Response response, String schema) {
        Allure.step("Схема " + schema, () -> response.then().body(matchesJsonSchemaInClasspath(schema)));
    }
}
