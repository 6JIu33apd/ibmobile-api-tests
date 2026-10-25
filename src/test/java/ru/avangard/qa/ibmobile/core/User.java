package ru.avangard.qa.ibmobile.core;

/**
 * Псевдонимы тестовых пользователей. Реальный логин на каждом контуре задаётся в users/&lt;env&gt;.properties,
 * пароль — в ~/.ibmobile/secrets.properties как &lt;env&gt;.&lt;login&gt;.password.
 * Если на контуре для псевдонима нет логина, кейсы под ним пропускаются.
 */
public enum User {

    /** Основной клиент: несколько рублёвых карточных счетов и валютные счета. */
    MAIN;

    public String login() {
        return Config.login(this);
    }

    public String password() {
        return Config.secret(login() + ".password");
    }
}
