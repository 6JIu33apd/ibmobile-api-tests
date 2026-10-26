package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Ответ ручки. Отказы приходят с HTTP 200: бизнес-отказ — checkResult с badItem ({@link #rejected}),
 * техническая ошибка — допустимый errorCode ({@link #error}). Ошибки сервера отсекает {@link Request}.
 */
public final class ApiResponse {

    /** Ответы без схемы (и все при -Dsamples=true) — материал для tools.SchemaDraft. */
    public static final Path SAMPLES = Path.of("target", "schema-samples");

    private final Endpoint endpoint;
    private final Response response;

    ApiResponse(Endpoint endpoint, Response response) {
        this.endpoint = endpoint;
        this.response = response;
    }

    public ApiResponse success() {
        if (response.path("errorCode") != null || "FAILED".equals(response.path("resultCode"))) {
            fail("%s вернул отказ: %s", endpoint, response.asString());
        }
        return this;
    }

    public ApiResponse error(ErrorCode expected) {
        assertThat(response.<Integer>path("errorCode")).as("errorCode: %s", response.asString()).isEqualTo(expected.code);
        assertThat(response.<String>path("errorDesc")).as("errorDesc").isEqualTo(expected.desc);
        return matches(expected.schema);
    }

    /** Бизнес-отказ: в checkResult есть поле badItem с сообщением message. */
    public ApiResponse rejected(String badItem, String message) {
        assertThat(response.<List<Map<String, Object>>>path("checkResult")).as("checkResult: %s", response.asString())
                .extracting(c -> c.get("badItem"), c -> c.get("errorMessage"))
                .contains(tuple(badItem, message));
        return matches("schemas/errors/check_failed.json");
    }

    public ApiResponse matchesSchema() {
        return matches(endpoint.schema(null));
    }

    /** Схема варианта ответа, например doc_type для документов. */
    public ApiResponse matchesSchema(String variant) {
        return matches(endpoint.schema(variant));
    }

    public <T> T path(String path) {
        return response.path(path);
    }

    /** Денежное значение без потери точности. */
    public BigDecimal decimal(String path) {
        return new BigDecimal(response.path(path).toString());
    }

    private ApiResponse matches(String schema) {
        Allure.step("Схема " + schema, () -> {
            boolean exists = getClass().getClassLoader().getResource(schema) != null;
            if (!exists || Boolean.getBoolean("samples")) {
                Path sample = SAMPLES.resolve(schema).resolve(UUID.randomUUID() + ".json");
                Files.createDirectories(sample.getParent());
                Files.writeString(sample, response.asString());
                if (!exists) {
                    fail("Нет схемы %s, ответ сохранён в %s — запусти tools.SchemaDraft", schema, sample);
                }
            }
            response.then().body(matchesJsonSchemaInClasspath(schema));
        });
        return this;
    }
}
