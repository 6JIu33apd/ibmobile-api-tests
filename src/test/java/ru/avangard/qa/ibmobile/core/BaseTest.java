package ru.avangard.qa.ibmobile.core;

import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import ru.avangard.qa.ibmobile.api.*;
import ru.avangard.qa.ibmobile.config.Config;

import java.lang.annotation.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.abort;

/** Контур, ключ логина и сессия одного кейса. */
public abstract class BaseTest {
    static {
        RestAssured.config = HttpFilter.CONFIG;
        RestAssured.filters(new HttpFilter());
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        Allure.getLifecycle().updateTest(result -> result.setName(testInfo.getDisplayName()));
        var method = testInfo.getTestMethod().orElseThrow();
        var annotations = Stream.of(method, method.getDeclaringClass(), getClass())
                .flatMap(element -> Stream.of(element.getAnnotations())).map(Annotation::annotationType).toList();
        Config.setEnv(annotations.contains(Prod.class) ? "prod" : annotations.contains(PreProd.class) ? "preprod" : "dev");
        Login login = method.getAnnotation(Login.class);
        Session.open(login != null ? login.value() : null);
    }

    @AfterEach
    void logout() { Session.close(); }

    /** GPath выбирает элемент; если его нет, кейс пропускается. */
    protected static <T> T find(JsonPath response, String path) {
        T value = response.get(path);
        return value != null ? value : abort("Нет данных: " + path);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @ResourceLock("preprod")
    @Tag("preprod")
    public @interface PreProd {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @ResourceLock("prod")
    @Tag("prod")
    public @interface Prod {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Login { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Story("Негативные")
    @DisplayName("Негативные")
    public @interface Negative {}
}
