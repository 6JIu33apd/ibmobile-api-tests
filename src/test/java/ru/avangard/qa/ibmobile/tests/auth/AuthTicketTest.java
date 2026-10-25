package ru.avangard.qa.ibmobile.tests.auth;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.avangard.qa.ibmobile.core.Profile;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.core.ErrorCode.SESSION_EXPIRED;

/** Логин и логаут проверяются на своих сессиях, поэтому без BaseTest. */
@Epic("Авторизация")
@Feature("Auth/ticket")
class AuthTicketTest {

    @DisplayName("Логин по логину и паролю")
    @ParameterizedTest(name = "{0}")
    @EnumSource(Profile.class)
    void login(Profile profile) {
        try (var s = Session.login(profile)) {
            s.loginResponse.matchesSchema();
            assertThat(s.ticket).isNotBlank();
        }
    }

    @DisplayName("Логаут: ticket перестаёт действовать")
    @ParameterizedTest(name = "{0}")
    @EnumSource(Profile.class)
    void logout(Profile profile) {
        try (var s = Session.login(profile)) {
            s.logout().matchesSchema();
            Request.to(ACC_LIST).as(s).post().error(SESSION_EXPIRED);
        }
    }
}
