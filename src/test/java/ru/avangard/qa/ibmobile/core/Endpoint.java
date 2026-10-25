package ru.avangard.qa.ibmobile.core;

/** Ручки бэка. {v} в пути — версия из versions/&lt;релиз&gt;.properties по имени константы. */
public enum Endpoint {

    AUTH_LOGIN("Auth/ticket/{v}", "Логин"),
    AUTH_LOGOUT("Auth/ticket/{v}", "Логаут"),
    SEND_CONFIRM_SMS("Auth/send_confirm_sms/{v}", "Отправка SMS-кода"),
    SHOW_SCRETCH("Auth/show_scretch/{v}", "Запрос кода со скретч-карты"),
    ACC_LIST("Statement/acc_list/{v}", "Список счетов"),
    PRIOR_INFO("Doc/prior_info/{v}", "Данные для документа"),
    PREPARE_DOC("Doc/prepare_doc/{v}", "Подготовка документа", true),
    SEND_DOC("Doc/send_doc/{v}", "Отправка документа"),
    CONV_RATES("Refs/conv_rates", "Курсы конвертации");

    private final String path;
    final String title;
    final boolean multipart;

    Endpoint(String path, String title) {
        this(path, title, false);
    }

    Endpoint(String path, String title, boolean multipart) {
        this.path = path;
        this.title = title;
        this.multipart = multipart;
    }

    String path() {
        return path.contains("{v}") ? path.replace("{v}", Config.version(this)) : path;
    }

    /** schemas/&lt;ручка&gt;/[&lt;вариант&gt;/]&lt;версия или schema&gt;.json */
    String schema(String variant) {
        String version = path.contains("{v}") ? Config.version(this) : "schema";
        return "schemas/" + name().toLowerCase() + "/" + (variant == null ? "" : variant + "/") + version + ".json";
    }
}
