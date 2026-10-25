package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.Assumptions;

import static ru.avangard.qa.ibmobile.api.Endpoint.AUTH_LOGIN;
import static ru.avangard.qa.ibmobile.api.Endpoint.AUTH_LOGOUT;

/**
 * Сессия пользователя: логин в начале теста, логаут в конце (даже если тест упал).
 * <pre>
 * try (var s = Session.login(User.MAIN)) {
 *     Request.to(ACC_LIST).as(s).post();
 * }
 * </pre>
 * Если для пользователя нет логина на текущем контуре, тест пропускается.
 */
public final class Session implements AutoCloseable {

    private final User user;
    private final String login;
    private final String ticket;
    private final ApiResponse loginResponse;
    private boolean loggedOut;

    private Session(User user, String login, String ticket, ApiResponse loginResponse) {
        this.user = user;
        this.login = login;
        this.ticket = ticket;
        this.loginResponse = loginResponse;
    }

    public static Session login(User user) {
        String login = user.login();
        Assumptions.assumeTrue(login != null, () -> "Нет логина для " + user + " на контуре " + Config.env());
        ApiResponse response = Request.to(AUTH_LOGIN)
                .param("login", login)
                .param("password", user.password())
                .post()
                .success();
        return new Session(user, login, response.path("ticket"), response);
    }

    public User user() {
        return user;
    }

    public String login() {
        return login;
    }

    public String ticket() {
        return ticket;
    }

    /** Ответ ручки логина, для проверок самого логина. */
    public ApiResponse loginResponse() {
        return loginResponse;
    }

    /** Явный логаут, когда его нужно проверить в тесте. */
    public ApiResponse logout() {
        loggedOut = true;
        return Request.to(AUTH_LOGOUT).as(this).delete();
    }

    /** Логаут после теста: его сбой попадает в отчёт, но не роняет тест. */
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
