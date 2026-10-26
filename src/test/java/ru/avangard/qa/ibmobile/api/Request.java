package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import ru.avangard.qa.ibmobile.config.Config;

import java.io.File;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Запрос к ручке, параметры — как в Postman. uid подставляется всегда, ticket — через {@link #as(Session)}.
 * Коллекция в значении — несколько одноимённых параметров. Тело — URLENC, у ручек с {@link Endpoint#multipart} — MULTIPART.
 */
public final class Request {

    /** Рудимент протокола: бэк принимает любой. */
    private static final String UID = "594874414557102";
    /** Соединение 10 с, ответ 60 с: зависший сервер роняет тест, а не держит прогон. */
    private static final RestAssuredConfig TIMEOUTS = RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
            .setParam("http.connection.timeout", 10_000)
            .setParam("http.socket.timeout", 60_000));

    private final Endpoint endpoint;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private final List<File> files = new ArrayList<>();

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

    /** Значение — toString(): строка, число, ObjectNode (JSON); коллекция — одноимённые параметры (secure в send_doc). */
    public Request param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    /** Файл из src/test/resources/files частью file. */
    public Request file(String name) {
        files.add(new File("src/test/resources/files", name));
        return this;
    }

    public ApiResponse post() {
        return send(Method.POST);
    }

    /** Только логаут. */
    ApiResponse delete() {
        return send(Method.DELETE);
    }

    private ApiResponse send(Method method) {
        String path = endpoint.path();
        return Allure.step(endpoint + ": " + method + " " + path, () -> {
            var spec = RestAssured.given().config(TIMEOUTS).baseUri(Config.get("baseUrl")).filter(new AllureRestAssured());
            if (endpoint.multipart) {
                multipart(spec);
            } else {
                spec.contentType(ContentType.URLENC.withCharset(UTF_8)).body(urlencoded());
            }
            Response response = spec.request(method, path);
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
                .flatMap(e -> values(e.getValue()).stream()
                        .map(v -> URLEncoder.encode(e.getKey(), UTF_8) + "=" + URLEncoder.encode(v, UTF_8)))
                .collect(Collectors.joining("&"));
    }

    /** Текстовые части — явно text/plain UTF-8, иначе кириллица в doc_content ломается. */
    private void multipart(RequestSpecification spec) {
        params.forEach((name, value) -> values(value).forEach(v -> spec.multiPart(
                new MultiPartSpecBuilder(v).controlName(name).mimeType("text/plain").charset(UTF_8).build())));
        files.forEach(file -> spec.multiPart("file", file, URLConnection.guessContentTypeFromName(file.getName())));
    }

    private static List<String> values(Object value) {
        return value instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of(String.valueOf(value));
    }
}
