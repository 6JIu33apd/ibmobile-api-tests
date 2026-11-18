package ru.avangard.qa.ibmobile.core;

import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.config.Config;

import static io.restassured.RestAssured.given;
import static ru.avangard.qa.ibmobile.api.Endpoint.*;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.*;

public record Session(Response loginResponse) {
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    public static void open(String loginKey) {
        Response response = given().formParam("login", Config.get(loginKey == null ? "login" : loginKey))
                .formParam("password", Config.get("password")).post(AUTH_LOGIN.path());
        success(response);
        CURRENT.set(new Session(response));
    }

    public static Session current() { return CURRENT.get(); }

    public String ticket() { return loginResponse.path("ticket"); }

    public Response logout() {
        Response response = given().delete(AUTH_LOGOUT.path());
        success(response);
        return response;
    }

    public static void close() {
        try {
            if (current() != null) current().logout();
        } catch (Exception | AssertionError ignored) {} finally {
            CURRENT.remove();
            Config.setEnv(null);
        }
    }
}
