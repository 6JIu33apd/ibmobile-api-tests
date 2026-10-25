package ru.avangard.qa.ibmobile.tests.auth;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.avangard.qa.ibmobile.core.BaseTest;
import ru.avangard.qa.ibmobile.core.Request;
import ru.avangard.qa.ibmobile.core.Section;
import ru.avangard.qa.ibmobile.core.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.core.Endpoint.ACC_LIST;
import static ru.avangard.qa.ibmobile.core.Endpoint.AUTH_LOGIN;
import static ru.avangard.qa.ibmobile.core.ErrorCode.SESSION_EXPIRED;
import static ru.avangard.qa.ibmobile.core.ErrorCode.WRONG_CREDENTIALS;
import static ru.avangard.qa.ibmobile.core.Profile.MAIN;

/** Логин и логаут проверяются на своих сессиях, поэтому без @Login. */
@Epic(Section.AUTH)
@Feature("Логин и логаут")
class AuthTicketTest extends BaseTest {

    @DisplayName("Логин по логину и паролю выдаёт ticket и данные клиента")
    @Test
    void login() {
        try (var s = Session.login(MAIN)) {
            s.loginResponse.matchesSchema();
            assertThat(s.ticket).isNotBlank();
        }
    }

    @DisplayName("После логаута ticket недействителен")
    @Test
    void logout() {
        try (var s = Session.login(MAIN)) {
            s.logout().matchesSchema();
            Request.to(ACC_LIST).as(s).post().error(SESSION_EXPIRED);
        }
    }

    /** Только несуществующий логин: неверный пароль реального профиля копит попытки до блокировки (5, без сброса). */
    @Story("Негативные")
    @DisplayName("Логин несуществующего пользователя отклоняется")
    @Test
    void unknownLogin() {
        Request.to(AUTH_LOGIN).param("login", "no_such_login_qa_000").param("password", "x").post().error(WRONG_CREDENTIALS);
    }
}
