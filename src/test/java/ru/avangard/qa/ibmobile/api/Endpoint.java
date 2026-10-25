package ru.avangard.qa.ibmobile.api;

import ru.avangard.qa.ibmobile.core.Config;

/**
 * Ручки бэка. {v} в пути заменяется версией из versions/&lt;release&gt;.properties (ключ = имя константы).
 * Схема ответа ищется в schemas/&lt;имя константы в нижнем регистре&gt;/[вариант/]&lt;версия&gt;.json.
 */
public enum Endpoint {

    AUTH_LOGIN("Auth/ticket/{v}", "Логин"),
    AUTH_LOGOUT("Auth/ticket/{v}", "Логаут"),
    SEND_CONFIRM_SMS("Auth/send_confirm_sms/{v}", "Отправка SMS-кода подтверждения"),
    SHOW_SCRETCH("Auth/show_scretch/{v}", "Запрос кода с карты"),
    ACC_LIST("Statement/acc_list/{v}", "Список счетов"),
    PRIOR_INFO("Doc/prior_info/{v}", "Данные для документа"),
    PREPARE_DOC("Doc/prepare_doc/{v}", "Подготовка документа", "7.6"),
    SEND_DOC("Doc/send_doc/{v}", "Отправка документа"),
    CONV_RATES("Refs/conv_rates", "Курсы конвертации");

    private final String path;
    private final String title;
    /** Версия, начиная с которой ручка принимает только MULTIPART; null — всегда URLENC. */
    private final String multipartSince;

    Endpoint(String path, String title) {
        this(path, title, null);
    }

    Endpoint(String path, String title, String multipartSince) {
        this.path = path;
        this.title = title;
        this.multipartSince = multipartSince;
    }

    public String title() {
        return title;
    }

    public boolean isVersioned() {
        return path.contains("{v}");
    }

    /** Версия в текущем релизе, null для ручек без версии. */
    public String version() {
        return isVersioned() ? Config.version(name()) : null;
    }

    public String path() {
        return isVersioned() ? path.replace("{v}", version()) : path;
    }

    public boolean isMultipart() {
        if (multipartSince == null) {
            return false;
        }
        return !isVersioned() || Runtime.Version.parse(version()).compareTo(Runtime.Version.parse(multipartSince)) >= 0;
    }

    /** Путь к схеме в classpath; variant — подпапка, например doc_type для prepare_doc. */
    public String schema(String variant) {
        return "schemas/" + name().toLowerCase()
                + (variant == null ? "" : "/" + variant)
                + "/" + (isVersioned() ? version() : "schema") + ".json";
    }
}
