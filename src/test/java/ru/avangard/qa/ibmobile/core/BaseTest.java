package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInfo;
import ru.avangard.qa.ibmobile.api.Session;
import ru.avangard.qa.ibmobile.config.Config;
import ru.avangard.qa.ibmobile.config.Profile;

import java.util.Arrays;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Базовый класс всех тестов. На prod тесты класса без @Tag("prod") пропускаются.
 * {@link #session()} — сессия теста под {@link Profile#MAIN} или профилем из {@link Login} на методе:
 * логин при первом обращении, логаут после теста. Тест без session() не логинится.
 */
public abstract class BaseTest {

    private Profile profile;
    private Session session;

    @BeforeEach
    void profile(TestInfo test) {
        assumeTrue(!"prod".equals(Config.ENV) || Arrays.stream(getClass().getAnnotationsByType(Tag.class))
                .anyMatch(tag -> "prod".equals(tag.value())), "На prod только классы с @Tag(\"prod\")");
        Login login = test.getTestMethod().orElseThrow().getAnnotation(Login.class);
        profile = login != null ? login.value() : Profile.MAIN;
    }

    /** Экземпляр класса — на каждый тест (JUnit по умолчанию), поэтому сессии параллельных тестов не пересекаются. */
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
}
