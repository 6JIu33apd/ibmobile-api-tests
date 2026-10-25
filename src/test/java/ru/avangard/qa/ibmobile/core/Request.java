package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.config.EncoderConfig;
import io.restassured.config.MultiPartConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import ru.avangard.qa.ibmobile.api.Endpoint;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Запрос к ручке. Параметры называются так же, как в Postman.
 * uid подставляется всегда, ticket — через {@link #as(Session)}.
 * URLENC или MULTIPART выбирается по ручке и её версии, тесту об этом знать не нужно.
 * <pre>
 * Request.to(ACC_LIST).as(session).param("account", acc).post()
 * </pre>
 */
public final class Request {

    private static final RestAssuredConfig CONFIG = RestAssured.config()
            .encoderConfig(EncoderConfig.encoderConfig().defaultContentCharset("UTF-8"))
            .multiPartConfig(MultiPartConfig.multiPartConfig().defaultCharset("UTF-8"));

    private final Endpoint endpoint;
    private final Map<String, Object> params = new LinkedHashMap<>();
    private final List<File> files = new ArrayList<>();

    private Request(Endpoint endpoint) {
        this.endpoint = endpoint;
        params.put("uid", Config.uid());
    }

    public static Request to(Endpoint endpoint) {
        return new Request(endpoint);
    }

    /** Запрос от имени сессии: добавляет ticket. */
    public Request as(Session session) {
        return param("ticket", session.ticket());
    }

    /**
     * Добавляет или заменяет параметр. Объекты (например, {@link Template}) передаются строкой,
     * коллекция — несколькими одноимёнными параметрами (как secure у send_doc).
     */
    public Request param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public Request params(Map<String, ?> values) {
        params.putAll(values);
        return this;
    }

    /** Убирает параметр, например uid, для негативных проверок. */
    public Request without(String name) {
        params.remove(name);
        return this;
    }

    /** Прикладывает файл из src/test/resources/files. */
    public Request file(String name) {
        URL url = Request.class.getClassLoader().getResource("files/" + name);
        if (url == null) {
            throw new IllegalArgumentException("Нет файла files/" + name);
        }
        try {
            files.add(new File(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
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

    private static List<String> values(Object value) {
        return value instanceof Collection<?> c
                ? c.stream().map(String::valueOf).toList()
                : List.of(String.valueOf(value));
    }

    /** Тело URLENC собирается вручную: allure-rest-assured не умеет показывать одноимённые form-параметры. */
    private String urlencoded() {
        return params.entrySet().stream()
                .flatMap(e -> values(e.getValue()).stream()
                        .map(v -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)))
                .collect(Collectors.joining("&"));
    }

    private ApiResponse send(Method method) {
        String path = endpoint.path();
        return Allure.step(endpoint.title() + ": " + method + " " + path, () -> {
            RequestSpecification spec = RestAssured.given()
                    .config(CONFIG)
                    .baseUri(Config.baseUrl())
                    .filter(new AllureRestAssured());
            if (endpoint.isMultipart()) {
                params.forEach((name, value) -> values(value).forEach(v -> spec.multiPart(new MultiPartSpecBuilder(v)
                        .controlName(name).mimeType("text/plain").charset("UTF-8").build())));
                files.forEach(file -> spec.multiPart("file", file));
            } else if (method == Method.GET) {
                params.forEach((name, value) -> spec.queryParam(name, String.valueOf(value)));
            } else {
                spec.contentType(ContentType.URLENC.withCharset(StandardCharsets.UTF_8)).body(urlencoded());
            }
            Response response = spec.request(method, path);
            assertThat(response.statusCode())
                    .as("HTTP-статус %s %s (не 200 — упал нижний уровень):%n%s", method, path, response.asString())
                    .isEqualTo(200);
            return new ApiResponse(endpoint, response);
        });
    }
}
