package ru.avangard.qa.ibmobile.tests.auth;

import io.qameta.allure.*;
import org.junit.jupiter.api.*;
import ru.avangard.qa.ibmobile.core.*;
import ru.avangard.qa.ibmobile.tests.Section;

import static io.restassured.RestAssured.given;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ErrorCode.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.*;

/** Контракт логина и логаута; недействительность ticket после выхода проверяется через acc_list. */
@Epic(Section.AUTH)
@Feature("Логин и логаут")
class AuthTicketTest extends BaseTest {
    @DisplayName("Логин по логину и паролю выдаёт ticket и данные клиента")
    @Test
    void login() {
        matchesSchema(Session.current().loginResponse(), AUTH_LOGIN.schema());
    }

    @DisplayName("После логаута ticket недействителен")
    @Test
    void logout() {
        matchesSchema(Session.current().logout(), AUTH_LOGOUT.schema());
        error(given().post(ACC_LIST.path()), SESSION_EXPIRED);
    }

    @Nested @Negative
    class Negatives {
        @DisplayName("Логин несуществующего пользователя отклоняется")
        @Test
        void unknownLogin() {
            error(given().formParam("login", "no_such_login_qa_000").formParam("password", "x").post(AUTH_LOGIN.path()), WRONG_CREDENTIALS);
        }
    }
}
