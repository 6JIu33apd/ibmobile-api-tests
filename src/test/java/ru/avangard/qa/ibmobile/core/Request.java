package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Запрос к ручке, параметры — как в Postman. uid подставляется всегда, ticket — через {@link #as(Session)}.
 * Коллекция в значении — несколько одноимённых параметров.
 */
public final class Request {

    private final Endpoint endpoint;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private final List<File> files = new ArrayList<>();

    private Request(Endpoint endpoint) {
        this.endpoint = endpoint;
        params.put("uid", Config.get("uid"));
    }

    public static Request to(Endpoint endpoint) {
        return new Request(endpoint);
    }

    public Request as(Session session) {
        return param("ticket", session.ticket);
    }

    public Request param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public Request params(Map<String, ?> values) {
        params.putAll(values);
        return this;
    }

    /** Файл из src/test/resources/files. */
    public Request file(String name) {
        files.add(new File("src/test/resources/files", name));
        return this;
    }

    public ApiResponse post() {
        return send(Method.POST);
    }

    public ApiResponse get() {
        return send(Method.GET);
    }

    public ApiResponse delete() {
        return send(Method.DELETE);
    }

    private ApiResponse send(Method method) {
        String path = endpoint.path();
        return Allure.step(endpoint.title + ": " + method + " " + path, () -> {
            var spec = RestAssured.given().baseUri(Config.get("baseUrl")).filter(new AllureRestAssured());
            if (endpoint.multipart) {
                params.forEach((name, value) -> values(value).forEach(v -> spec.multiPart(
                        new MultiPartSpecBuilder(v).controlName(name).mimeType("text/plain").charset(UTF_8).build())));
                files.forEach(file -> spec.multiPart("file", file));
            } else if (method == Method.GET) {
                params.forEach((name, value) -> spec.queryParam(name, String.valueOf(value)));
            } else {
                // тело собирается вручную: allure-rest-assured падает на одноимённых form-параметрах
                spec.contentType(ContentType.URLENC.withCharset(UTF_8)).body(params.entrySet().stream()
                        .flatMap(e -> values(e.getValue()).stream()
                                .map(v -> URLEncoder.encode(e.getKey(), UTF_8) + "=" + URLEncoder.encode(v, UTF_8)))
                        .collect(Collectors.joining("&")));
            }
            Response response = spec.request(method, path);
            assertThat(response.statusCode()).as("HTTP %s %s: %s", method, path, response.asString()).isEqualTo(200);
            return new ApiResponse(endpoint, response);
        });
    }

    private static List<String> values(Object value) {
        return value instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of(String.valueOf(value));
    }
}
