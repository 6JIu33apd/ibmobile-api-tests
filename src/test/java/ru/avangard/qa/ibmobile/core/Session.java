package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.Allure;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.config.Config;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.success;

public final class Session {
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private final String loginKey;
    private Response loginResponse;
    private boolean loggedOut;

    private Session(String loginKey) {
        this.loginKey = loginKey == null ? "login" : loginKey;
    }

    public static void open(String loginKey) {
        CURRENT.set(new Session(loginKey));
    }

    public static Session current() {
        return CURRENT.get();
    }

    public Response login() {
        if (loginResponse == null) {
            String login = Config.settings().getProperty(loginKey);
            assumeTrue(login != null, "Нет логина " + loginKey);
            loginResponse = given().formParam("login", login).formParam("password", Config.get("password")).post(AUTH_LOGIN.path());
            success(loginResponse);
        }
        return loginResponse;
    }

    public String ticket() {
        return login().path("ticket");
    }

    public Response logout() {
        loggedOut = true;
        Response response = given().delete(AUTH_LOGOUT.path());
        success(response);
        return response;
    }

    public static void close() {
        Session session = CURRENT.get();
        try {
            if (session != null && session.loginResponse != null && !session.loggedOut) session.logout();
        } catch (Exception | AssertionError e) {
            Allure.attachment("Логаут не удался", e.toString());
        } finally {
            CURRENT.remove();
        }
    }
}
