package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.authentication.ExplicitNoAuthScheme;
import io.restassured.common.config.NumberReturnType;
import io.restassured.config.*;
import io.restassured.filter.*;
import io.restassured.response.Response;
import io.restassured.specification.*;
import ru.avangard.qa.ibmobile.config.Config;
import ru.avangard.qa.ibmobile.core.Session;
import ru.avangard.qa.ibmobile.tools.SchemaDraft;

import java.time.Duration;
import java.util.*;

import static io.restassured.http.ContentType.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.fail;

public final class HttpFilter implements Filter {
    public static final RestAssuredConfig CONFIG = RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig().connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofMinutes(2))).jsonConfig(JsonConfig.jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL));
    private static final AllureRestAssured ALLURE = new AllureRestAssured();
    private static final Endpoint[] ENDPOINTS = Endpoint.values();

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec, FilterContext context) {
        Endpoint endpoint = Arrays.stream(ENDPOINTS)
                .filter(e -> e.path().equals(request.getUserDefinedPath()) && e.method.name().equals(request.getMethod()))
                .findFirst().orElseThrow();

        return Allure.step("Запрос " + request.getUserDefinedPath(), () -> {
            Object docType = request.getFormParams().get("doc_type");
            prepare(request, endpoint);
            Response response = ALLURE.filter(request, responseSpec, context);
            if (responseSpec.getStatusCode() == null && response.statusCode() != 200)
                fail("Ошибка сервера: HTTP %s, %s %s: %s", response.statusCode(), request.getMethod(), request.getUserDefinedPath(), response.asString());
            if (response.statusCode() == 200 && !Boolean.getBoolean("schemaDraft.skip"))
                SchemaDraft.capture(endpoint, docType, response);
            return response;
        });
    }

    private static void prepare(FilterableRequestSpecification request, Endpoint endpoint) {
        request.baseUri(Config.get("baseurl")).formParam("uid", "594874414557102");
        if (endpoint != Endpoint.AUTH_LOGIN && !(request.getAuthenticationScheme() instanceof ExplicitNoAuthScheme) && !request.getFormParams().containsKey("ticket"))
            request.formParam("ticket", Session.current().ticket());

        if (endpoint.contentType == MULTIPART) {
            request.contentType(MULTIPART);
            multipart(request);
        } else request.contentType(endpoint.contentType.withCharset(UTF_8));
    }

    private static void multipart(FilterableRequestSpecification request) {
        var params = new LinkedHashMap<>(request.getFormParams());
        params.forEach((name, value) -> request.removeFormParam(name).multiPart(name, value, TEXT.withCharset(UTF_8)));
        Allure.attachment("Параметры запроса", params.toString());
    }
}