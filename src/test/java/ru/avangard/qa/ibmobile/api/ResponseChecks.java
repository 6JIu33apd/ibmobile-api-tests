package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import io.restassured.response.Response;
import io.restassured.path.json.JsonPath;

import java.util.*;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.*;

/**
 * Проверки ответов Rest Assured: успех, техническая ошибка, бизнес-отказ и JSON-схема.
 */
public final class ResponseChecks {
    private ResponseChecks() {
    }

    /**
     * Отсекает признаки отказа; структуру и обязательные поля проверяет схема.
     */
    public static JsonPath success(Response response) {
        var json = response.jsonPath();
        String resultCode = json.get("resultCode");
        if (json.get("errorCode") != null || Integer.valueOf(-1).equals(json.get("responseCode"))
                || (resultCode != null && !"SUCCESS".equals(resultCode))
                || Boolean.FALSE.equals(json.get("checkSuccess")) || Boolean.TRUE.equals(json.get("secureCheck.errorExist")))
            fail("Ответ содержит отказ: %s", response.asString());
        return json;
    }

    /**
     * Сверяет errorCode, errorDesc и схему соответствующего формата ошибки.
     */
    public static void error(Response response, ErrorCode expected) {
        assertThat(response.jsonPath().getMap("")).as("ошибка: %s", response.asString())
                .containsEntry("errorCode", expected.errorCode).containsEntry("errorDesc", expected.errorDesc);
        matchesSchema(response, ErrorCode.SCHEMA);
    }

    /**
     * Ищет ошибку с заданными badItem и errorMessage, затем проверяет схему бизнес-отказа.
     */
    public static void rejected(Response response, String badItem, String errorMessage) {
        assertThat(response.<List<Map<String, Object>>>path("checkResult")).as("checkResult: %s", response.asString())
                .extracting(check -> check.get("badItem"), check -> check.get("errorMessage"), check -> check.get("error"))
                .contains(tuple(badItem, errorMessage, true));
        matchesSchema(response, "schemas/errors/check_failed.json");
    }

    /**
     * Проверяет утверждённую схему; черновик не подменяет эталон даже при отсутствии файла.
     */
    public static void matchesSchema(Response response, String schema) {
        Allure.step("Схема " + schema, () -> response.then().body(matchesJsonSchemaInClasspath(schema)));
    }
}
