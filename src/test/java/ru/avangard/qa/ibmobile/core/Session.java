package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.Allure;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.config.Config;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseChecks.success;

public final class Session {
    public final String ticket;
    public final Response loginResponse;
    private boolean loggedOut;

    public Session(String loginKey) {
        String login = Config.settings().getProperty(loginKey);
        assumeTrue(login != null, "Нет логина " + loginKey);
        loginResponse = given().formParam("login", login).formParam("password", Config.get("password")).post(AUTH_LOGIN.path());
        ticket = success(loginResponse).get("ticket");
    }

    public Response logout() {
        loggedOut = true;
        Response response = given().formParam("ticket", ticket).delete(AUTH_LOGOUT.path());
        success(response);
        return response;
    }

    public void close() {
        if (loggedOut) return;
        try {
            logout();
        } catch (Exception | AssertionError e) {
            Allure.attachment("Логаут не удался", e.toString());
        }
    }
}
