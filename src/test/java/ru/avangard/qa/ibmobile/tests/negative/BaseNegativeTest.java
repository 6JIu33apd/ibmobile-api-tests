package ru.avangard.qa.ibmobile.tests.negative;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.api.Endpoint;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Session;
import ru.avangard.qa.ibmobile.tests.Section;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.api.ErrorCode.SESSION_EXPIRED;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.error;

@Epic(Section.NEGATIVE)
@Feature("Общие негативные")
public class BaseNegativeTest extends BaseTest {

    static Stream<Endpoint> endpoints() {
        return Arrays.stream(Endpoint.values())
                .filter(e -> e != Endpoint.AUTH_LOGIN && e != Endpoint.AUTH_LOGOUT);
    }

    static Stream<Arguments> invalidTicketCases() {
        return endpoints().flatMap(e -> Stream.of(
                arguments(e, named("без тикета", Map.of())),
                arguments(e, named("с пустым тикетом", Map.of("ticket", ""))),
                arguments(e, named("с левым тикетом", Map.of("ticket", "D6evIЕBf021р653974IffпNлPgoZhPWDВBTzr7TF")))
        ));
    }

    @ParameterizedTest(name = "Запрос {0} {1} отклоняется")
    @MethodSource("invalidTicketCases")
    void invalidTicket(Endpoint endpoint, Map<String, String> params) {
        error(given().auth().none().formParams(params).post(endpoint.path()), AUTHORIZATION_REQUIRED);
    }

    @ParameterizedTest(name = "Запрос {0} с истекшим тикетом отклоняется")
    @MethodSource("endpoints")
    void expiredTicket(Endpoint endpoint) {
        String ticket = Session.current().ticket();
        Session.current().logout();
        error(given().auth().none().formParam("ticket", ticket).post(endpoint.path()), SESSION_EXPIRED);
    }
}
