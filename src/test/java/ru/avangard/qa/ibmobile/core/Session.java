package ru.avangard.qa.ibmobile.core;

import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.config.Config;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.success;

public final class Session {
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private final Response loginResponse;

    private Session(String loginKey) {
        String login = Config.settings().getProperty(loginKey);
        assumeTrue(login != null, "Нет логина " + loginKey);
        loginResponse = given().formParam("login", login).formParam("password", Config.get("password")).post(AUTH_LOGIN.path());
        success(loginResponse);
    }

    public static void open(String loginKey) {
        CURRENT.set(new Session(loginKey == null ? "login" : loginKey));
    }

    public static Session current() {
        return CURRENT.get();
    }

    public Response loginResponse() {
        return loginResponse;
    }

    public String ticket() {
        return loginResponse.path("ticket");
    }

    public Response logout() {
        Response response = given().delete(AUTH_LOGOUT.path());
        success(response);
        return response;
    }
}
