package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;

import java.util.Arrays;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Базовый класс всех тестов.
 * На prod класс без @Tag("prod") пропускается целиком. С {@link Login} — сессия на класс
 * (нет профиля на контуре — класс пропускается); без него тест сам открывает сессии через {@link Session#login}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseTest {

    protected Session session;

    @BeforeAll
    void login() {
        assumeTrue(!"prod".equals(Config.ENV) || Arrays.stream(getClass().getAnnotationsByType(Tag.class))
                .anyMatch(tag -> "prod".equals(tag.value())), "На prod только классы с @Tag(\"prod\")");
        Login login = getClass().getAnnotation(Login.class);
        if (login != null) {
            session = Session.login(login.value());
        }
    }

    @AfterAll
    void logout() {
        if (session != null) {
            session.close();
        }
    }
}
