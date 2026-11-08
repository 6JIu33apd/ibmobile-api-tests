package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import ru.avangard.qa.ibmobile.config.Config;
import ru.avangard.qa.ibmobile.config.Profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static ru.avangard.qa.ibmobile.api.Endpoint.AUTH_LOGIN;
import static ru.avangard.qa.ibmobile.api.Endpoint.AUTH_LOGOUT;

/**
 * Сессия теста (BaseTest): логин профиля и логаут. Логаут в close() не роняет тест.
 */
public final class Session {

    public final String login;
    /** Нужен для подтверждения PWD. */
    public final String password;
    public final String ticket;
    /** Для проверки схемы логина. */
    public final ApiResponse loginResponse;
    private boolean loggedOut;

    private Session(String login, String password, ApiResponse loginResponse) {
        this.login = login;
        this.password = password;
        this.ticket = loginResponse.path("ticket");
        assertThat(ticket).as("ticket").isNotBlank();
        this.loginResponse = loginResponse;
    }

    /** Нет профиля на контуре — тест пропускается. */
    public static Session login(Profile profile) {
        String login = Config.find(profile + ".login");
        assumeTrue(login != null, "Нет профиля " + profile + " на контуре " + Config.env());
        String password = Config.get(profile + ".password");
        return new Session(login, password, Request.to(AUTH_LOGIN).param("login", login).param("password", password).post().success());
    }

    public ApiResponse logout() {
        loggedOut = true;
        return Request.to(AUTH_LOGOUT).as(this).delete().success().matchesSchema();
    }

    /** Повторно не разлогинивает; ошибка логаута — вложение в Allure, а не падение теста. */
    public void close() {
        if (loggedOut) {
            return;
        }
        try {
            logout();
        } catch (Exception | AssertionError e) {
            Allure.attachment("Логаут не удался", e.toString());
        }
    }
}
