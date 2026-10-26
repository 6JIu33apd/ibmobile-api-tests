package ru.avangard.qa.ibmobile.config;

/** Профили: &lt;env&gt;.&lt;профиль&gt;.login / .password в ~/.ibmobile/secrets.properties. */
public enum Profile {

    /** Рублёвые дебетовые (2) и кредитный, рублёвый и валютные текущие, депозитный счета. */
    MAIN;

    /** null, если на контуре профиля нет. */
    public String login() {
        return Config.find(name() + ".login");
    }

    public String password() {
        return Config.get(name() + ".password");
    }
}
