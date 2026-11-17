package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.authentication.ExplicitNoAuthScheme;
import io.restassured.common.config.NumberReturnType;
import io.restassured.config.*;
import io.restassured.filter.*;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.*;
import ru.avangard.qa.ibmobile.config.Config;
import ru.avangard.qa.ibmobile.core.Session;
import ru.avangard.qa.ibmobile.tools.SchemaDraft;

import java.time.Duration;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.api.Endpoint.AUTH_LOGIN;

/** Общие параметры запроса, Allure и проверки HTTP/errorCode. */
public final class HttpFilter implements Filter {
    public static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                    .connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofMinutes(2)))
            .jsonConfig(JsonConfig.jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL));

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec, FilterContext context) {
        String target = request.getMethod() + " " + request.getUserDefinedPath();
        return Allure.step("Запрос " + target, () -> {
            request.baseUri(Config.get("baseurl")).formParam("uid", "594874414557102");
            if (!(request.getAuthenticationScheme() instanceof ExplicitNoAuthScheme) && !request.getFormParams().containsKey("ticket")
                    && !(request.getMethod().equals(AUTH_LOGIN.method.name()) && request.getUserDefinedPath().equals("/" + AUTH_LOGIN.path())))
                request.formParam("ticket", Session.current().ticket());
            Map<String, Object> params = new LinkedHashMap<>(request.getFormParams());
            if (ContentType.MULTIPART.matches(request.getContentType())) multipart(request, params);
            else request.contentType(ContentType.URLENC.withCharset(UTF_8));
            Response response = new AllureRestAssured().filter(request, responseSpec, context);
            SchemaDraft.capture(request.getMethod(), request.getUserDefinedPath(), params, response);
            assertThat(response.statusCode()).as("Ошибка сервера: HTTP %s: %s", target, response.asString()).isEqualTo(200);
            Integer errorCode = response.path("errorCode");
            assertThat(errorCode == null || Arrays.stream(ErrorCode.values()).anyMatch(error -> error.errorCode == errorCode))
                    .as("Ошибка сервера: errorCode %s, %s: %s", errorCode, target, response.asString()).isTrue();
            return response;
        });
    }

    private static void multipart(FilterableRequestSpecification request, Map<String, Object> params) {
        params.forEach((name, value) -> request.removeFormParam(name)
                .multiPart(name, value.toString(), ContentType.TEXT.withCharset(UTF_8)));
        Allure.attachment("Параметры запроса", params.toString());
    }
}
