package ru.avangard.qa.ibmobile.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import ru.avangard.qa.ibmobile.config.Config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Запрос к ручке, параметры — как в Postman. uid подставляется всегда, ticket — через {@link #as(Session)}.
 * Коллекция в значении — несколько одноимённых параметров. Тело — URLENC, у ручек с {@link Endpoint#multipart} — MULTIPART.
 * Любая ручка бэка вызывается только так: {@code Request.to(endpoint)...post()}.
 */
public final class Request {

    /** Рудимент протокола: бэк принимает любой. */
    private static final String UID = "594874414557102";
    /**
     * Соединение 10 с, ответ 2 мин: зависший сервер роняет тест, а не держит прогон.
     * Дробные числа ответа — BigDecimal: по умолчанию Float, и 12345678.91 читается как 12345679.
     */
    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                    .setParam("http.connection.timeout", 10_000)
                    .setParam("http.socket.timeout", 120_000))
            .jsonConfig(JsonConfig.jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL));

    private final Endpoint endpoint;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private final List<String> files = new ArrayList<>();

    private Request(Endpoint endpoint) {
        this.endpoint = endpoint;
        params.put("uid", UID);
    }

    public static Request to(Endpoint endpoint) {
        return new Request(endpoint);
    }

    /** ticket — form-параметр, как и все остальные. */
    public Request as(Session session) {
        return param("ticket", session.ticket);
    }

    /**
     * Значение — toString(): строка, число, ObjectNode (JSON); коллекция — одноимённые параметры (secure в send_doc).
     * null убирает параметр: кейс «без параметра» — строка таблицы, как {@code DocContent.set} для полей документа.
     */
    public Request param(String name, Object value) {
        if (value == null) {
            params.remove(name);
        } else {
            params.put(name, value);
        }
        return this;
    }

    /** Параметры prepare_doc: doc_type, doc_content; файлы из src/test/resources/files — части file и attachmentsList. */
    public Request doc(DocType docType, ObjectNode docContent, String... files) {
        if (files.length > 0) {
            var attachmentsList = docContent.putArray("attachmentsList");
            for (String file : files) {
                attachmentsList.addObject().put("filename", file).put("contentType", contentType(file));
                this.files.add(file);
            }
        }
        return param("doc_type", docType).param("doc_content", docContent);
    }

    public ApiResponse post() {
        return execute(Method.POST);
    }

    /** Только логаут. */
    ApiResponse delete() {
        return execute(Method.DELETE);
    }

    private ApiResponse execute(Method method) {
        String path = endpoint.path();
        return Allure.step(endpoint + ": " + method + " " + path, () -> {
            var specification = RestAssured.given().config(CONFIG).baseUri(Config.get("baseUrl")).filter(new AllureRestAssured());
            if (endpoint.multipart) {
                multipart(specification);
            } else {
                specification.contentType(ContentType.URLENC.withCharset(UTF_8)).body(urlencoded());
            }
            Response response = specification.request(method, path);
            // ошибки сервера роняют любой тест, в том числе негативный
            assertThat(response.statusCode()).as("Ошибка сервера: HTTP %s %s: %s", method, path, response.asString()).isEqualTo(200);
            Integer errorCode = response.path("errorCode");
            if (errorCode != null && !ErrorCode.allowed(errorCode)) {
                fail("Ошибка сервера: недопустимый errorCode %s %s: %s", method, path, response.asString());
            }
            return new ApiResponse(endpoint, response);
        });
    }

    /** Тело собирается вручную: allure-rest-assured падает на одноимённых form-параметрах. */
    private String urlencoded() {
        return params.entrySet().stream()
                .flatMap(param -> values(param.getValue()).stream()
                        .map(value -> URLEncoder.encode(param.getKey(), UTF_8) + "=" + URLEncoder.encode(value, UTF_8)))
                .collect(Collectors.joining("&"));
    }

    /** Текстовые части — явно text/plain UTF-8, иначе кириллица в doc_content ломается. */
    private void multipart(RequestSpecification specification) {
        params.forEach((name, value) -> values(value).forEach(text -> specification.multiPart(
                new MultiPartSpecBuilder(text).controlName(name).mimeType("text/plain").charset(UTF_8).build())));
        files.forEach(file -> specification.multiPart("file", file, bytes(file), contentType(file)));
    }

    private static byte[] bytes(String file) {
        try (var in = Objects.requireNonNull(Request.class.getResourceAsStream("/files/" + file), "Нет files/" + file)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Тип по расширению; неизвестный — ошибка, а не contentType: null в attachmentsList. */
    private static String contentType(String file) {
        return Objects.requireNonNull(URLConnection.guessContentTypeFromName(file), "Неизвестный тип файла " + file);
    }

    private static List<String> values(Object value) {
        return value instanceof Collection<?> collection ? collection.stream().map(String::valueOf).toList() : List.of(String.valueOf(value));
    }
}
