package ru.avangard.qa.ibmobile.tests.auth;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.core.Endpoint;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;
import ru.avangard.qa.ibmobile.core.Profile;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.core.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.core.Endpoint.PRIOR_INFO;
import static ru.avangard.qa.ibmobile.core.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.core.ErrorCode.AUTHORIZATION_REQUIRED;
import static ru.avangard.qa.ibmobile.core.ErrorCode.SESSION_EXPIRED;

/**
 * Одинаковые для всех ручек проверки ticket.
 * Новая ручка с ticket добавляется в {@link #endpoints()} вместе с параметрами,
 * без которых она не доходит до проверки авторизации.
 */
@Epic("Авторизация")
@Feature("Общие проверки ticket")
class CommonAuthTest {

    static Stream<Arguments> endpoints() {
        return Stream.of(
                arguments(ACC_LIST, Map.of()),
                arguments(PRIOR_INFO, Map.of()),
                // без doc_type и doc_content 8.1 отвечает HTTP 500
                arguments(PREPARE_DOC, Map.of("doc_type", "ib_our_acc_transfer", "doc_content", "{}")),
                arguments(SEND_DOC, Map.of()));
    }

    @DisplayName("Без ticket")
    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void withoutTicket(Endpoint endpoint, Map<String, String> params) {
        Request.to(endpoint).params(params).post().error(AUTHORIZATION_REQUIRED);
    }

    @DisplayName("Несуществующий ticket")
    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void invalidTicket(Endpoint endpoint, Map<String, String> params) {
        Request.to(endpoint).params(params).param("ticket", "INVALID_TICKET").post().error(AUTHORIZATION_REQUIRED);
    }

    @DisplayName("ticket после логаута")
    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void expiredTicket(Endpoint endpoint, Map<String, String> params) {
        try (var s = Session.login(Profile.MAIN)) {
            s.logout();
            Request.to(endpoint).params(params).as(s).post().error(SESSION_EXPIRED);
        }
    }
}
