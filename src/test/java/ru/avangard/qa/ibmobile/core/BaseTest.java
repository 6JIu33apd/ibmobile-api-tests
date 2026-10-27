package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import ru.avangard.qa.ibmobile.api.Session;
import ru.avangard.qa.ibmobile.config.Config;
import ru.avangard.qa.ibmobile.config.Profile;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Базовый класс всех тестов. Контур — dev, {@link OnTest} / {@link OnProd} на методе или классе — test / prod.
 * {@link #session()} — сессия теста под {@link Profile#MAIN} или профилем из {@link Login} на методе:
 * логин при первом обращении, логаут после теста. Тест без session() не логинится.
 */
public abstract class BaseTest {

    private Profile profile;
    private Session session;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        Method method = testInfo.getTestMethod().orElseThrow();
        Config.env(on(method, OnProd.class) ? "prod" : on(method, OnTest.class) ? "test" : "dev");
        Login login = method.getAnnotation(Login.class);
        profile = login != null ? login.value() : Profile.MAIN;
    }

    /** Экземпляр класса — на каждый тест (JUnit по умолчанию, и в {@link Negative}): сессии тестов не пересекаются. */
    protected Session session() {
        if (session == null) {
            session = Session.login(profile);
        }
        return session;
    }

    @AfterEach
    void logout() {
        if (session != null) {
            session.close();
        }
    }

    /** Аннотация на методе, его классе (в том числе {@link Negative}) или классе теста. */
    private boolean on(Method method, Class<? extends Annotation> env) {
        return method.isAnnotationPresent(env) || method.getDeclaringClass().isAnnotationPresent(env) || getClass().isAnnotationPresent(env);
    }
}
