package ru.avangard.qa.ibmobile.api;

import io.qameta.allure.Allure;
import ru.avangard.qa.ibmobile.config.Config;
import ru.avangard.qa.ibmobile.config.Profile;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static ru.avangard.qa.ibmobile.api.Endpoint.AUTH_LOGIN;
import static ru.avangard.qa.ibmobile.api.Endpoint.AUTH_LOGOUT;

/**
 * Сессия. Обычно — одна на класс из BaseTest; своя ({@code try (var s = Session.login(...))}) нужна тестам,
 * которые разлогинивают: логаут, смена логина/пароля. Логаут в close() не роняет тест.
 */
public final class Session implements AutoCloseable {

    public final String login;
    public final String password;
    public final String ticket;
    public final ApiResponse loginResponse;
    private boolean loggedOut;

    private Session(String login, String password, ApiResponse loginResponse) {
        this.login = login;
        this.password = password;
        this.ticket = loginResponse.path("ticket");
        this.loginResponse = loginResponse;
    }

    /** Нет профиля на контуре — тест пропускается. */
    public static Session login(Profile profile) {
        assumeTrue(profile.login() != null, "Нет профиля " + profile + " на контуре " + Config.ENV);
        return login(profile.login(), profile.password());
    }

    public static Session login(String login, String password) {
        return new Session(login, password, Request.to(AUTH_LOGIN)
                .param("login", login)
                .param("password", password)
                .post().success());
    }

    public ApiResponse logout() {
        loggedOut = true;
        return Request.to(AUTH_LOGOUT).as(this).delete();
    }

    @Override
    public void close() {
        if (loggedOut) {
            return;
        }
        try {
            logout();
        } catch (Throwable e) {
            Allure.addAttachment("Логаут не удался", e.toString());
        }
    }
}
