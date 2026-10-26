package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import ru.avangard.qa.ibmobile.api.Session;
import ru.avangard.qa.ibmobile.config.Config;
import ru.avangard.qa.ibmobile.config.Profile;

import java.util.Arrays;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Базовый класс всех тестов. На prod класс без @Tag("prod") пропускается целиком.
 * {@link #session()} — сессия на класс под {@link Profile#MAIN} или профилем из {@link Login};
 * логин — при первом обращении, логаут — после класса. Тесты, которые разлогинивают, открывают свои через {@link Session#login}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseTest {

    private Session session;

    @BeforeAll
    void prodGuard() {
        assumeTrue(!"prod".equals(Config.ENV) || Arrays.stream(getClass().getAnnotationsByType(Tag.class))
                .anyMatch(tag -> "prod".equals(tag.value())), "На prod только классы с @Tag(\"prod\")");
    }

    protected synchronized Session session() {
        if (session == null) {
            Login login = getClass().getAnnotation(Login.class);
            session = Session.login(login != null ? login.value() : Profile.MAIN);
        }
        return session;
    }

    @AfterAll
    void logout() {
        if (session != null) {
            session.close();
        }
    }
}
