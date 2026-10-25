package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.Allure;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.api.Endpoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Ответ ручки. Бизнес-ошибки приходят с HTTP 200 и полем errorCode, успех — без errorCode.
 * <pre>
 * Request.to(ACC_LIST).as(s).post().success().matchesSchema();
 * Request.to(ACC_LIST).post().error(ErrorCode.AUTHORIZATION_REQUIRED);
 * </pre>
 */
public final class ApiResponse {

    /** Куда складываются ответы, для которых нет схемы; из них {@code tools.SchemaDraft} делает черновики. */
    public static final Path SAMPLES = Path.of("target", "schema-samples");

    private final Endpoint endpoint;
    private final Response response;

    ApiResponse(Endpoint endpoint, Response response) {
        this.endpoint = endpoint;
        this.response = response;
    }

    /** Ответ без бизнес-ошибки: нет errorCode и resultCode не FAILED (так отказывают документы). */
    public ApiResponse success() {
        assertThat(response.<Object>path("errorCode"))
                .as("%s вернул ошибку: %s", endpoint, response.asString())
                .isNull();
        assertThat(response.<String>path("resultCode"))
                .as("%s вернул отказ: %s", endpoint, response.asString())
                .isNotEqualTo("FAILED");
        return this;
    }

    /** Бизнес-ошибка с ожидаемым кодом; формат проверяется по схеме ошибки. */
    public ApiResponse error(ErrorCode expected) {
        assertThat(response.<Integer>path("errorCode"))
                .as("errorCode %s в ответе: %s", expected, response.asString())
                .isEqualTo(expected.code());
        assertThat(response.<String>path("errorDesc")).as("errorDesc").isEqualTo(expected.desc());
        return matches(expected.schema());
    }

    /** Проверка по схеме schemas/&lt;ручка&gt;/&lt;версия&gt;.json. */
    public ApiResponse matchesSchema() {
        return matches(endpoint.schema(null));
    }

    /** Проверка по схеме варианта ответа, например schemas/prepare_doc/&lt;doc_type&gt;/&lt;версия&gt;.json. */
    public ApiResponse matchesSchema(String variant) {
        return matches(endpoint.schema(variant));
    }

    public <T> T path(String path) {
        return response.path(path);
    }

    public JsonPath json() {
        return response.jsonPath();
    }

    public String body() {
        return response.asString();
    }

    public Response raw() {
        return response;
    }

    private ApiResponse matches(String schema) {
        Allure.step("Ответ соответствует схеме " + schema, () -> {
            if (Boolean.getBoolean("samples") || ApiResponse.class.getClassLoader().getResource(schema) == null) {
                Path sample = saveSample(schema);
                if (ApiResponse.class.getClassLoader().getResource(schema) == null) {
                    fail("Нет схемы %s. Ответ сохранён в %s, черновик схемы делает tools.SchemaDraft", schema, sample);
                }
            }
            response.then().body(matchesJsonSchemaInClasspath(schema));
        });
        return this;
    }

    private Path saveSample(String schema) throws IOException {
        Path dir = SAMPLES.resolve(schema);
        Files.createDirectories(dir);
        Path file = dir.resolve(UUID.randomUUID() + ".json");
        Files.writeString(file, response.asString(), StandardCharsets.UTF_8);
        return file;
    }
}
