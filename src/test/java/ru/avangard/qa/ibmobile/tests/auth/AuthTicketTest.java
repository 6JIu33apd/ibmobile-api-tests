package ru.avangard.qa.ibmobile.tests.auth;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;
import ru.avangard.qa.ibmobile.core.User;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static ru.avangard.qa.ibmobile.api.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.core.ErrorCode.SESSION_EXPIRED;

@Epic("Авторизация")
@Feature("Auth/ticket")
class AuthTicketTest {

    static Stream<Arguments> users() {
        return Stream.of(
                arguments("Основной клиент", User.MAIN));
    }

    @DisplayName("Логин по логину и паролю")
    @ParameterizedTest(name = "{0}")
    @MethodSource("users")
    void login(String title, User user) {
        try (var s = Session.login(user)) {
            s.loginResponse().matchesSchema();
            assertThat(s.ticket()).isNotBlank();
        }
    }

    @DisplayName("Логаут: ticket перестаёт действовать")
    @ParameterizedTest(name = "{0}")
    @MethodSource("users")
    void logout(String title, User user) {
        try (var s = Session.login(user)) {
            s.logout().matchesSchema();
            Request.to(ACC_LIST).as(s).post().error(SESSION_EXPIRED);
        }
    }
}
