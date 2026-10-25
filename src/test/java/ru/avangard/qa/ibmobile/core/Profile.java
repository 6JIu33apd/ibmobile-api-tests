package ru.avangard.qa.ibmobile.core;

/** Профили: &lt;env&gt;.&lt;профиль&gt;.login / .password в ~/.ibmobile/secrets.properties. */
public enum Profile {

    /** Рублёвые карточные счета (в т.ч. кредитный) и валютные счета. */
    MAIN;

    /** null, если на контуре профиля нет. */
    public String login() {
        return Config.find(name() + ".login");
    }

    public String password() {
        return Config.get(name() + ".password");
    }
}
