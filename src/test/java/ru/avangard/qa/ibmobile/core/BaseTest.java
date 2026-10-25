package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Сессия на класс под профилем из {@link Login}. Нет профиля на контуре — класс пропускается.
 * Тесты, которые сами ломают сессию (логаут, смена логина/пароля), открывают свою через {@link Session#login}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseTest {

    protected Session session;

    @BeforeAll
    void login() {
        Login login = getClass().getAnnotation(Login.class);
        if (login == null) {
            throw new IllegalStateException(getClass().getSimpleName() + ": нет @Login");
        }
        session = Session.login(login.value());
    }

    @AfterAll
    void logout() {
        if (session != null) {
            session.close();
        }
    }
}
