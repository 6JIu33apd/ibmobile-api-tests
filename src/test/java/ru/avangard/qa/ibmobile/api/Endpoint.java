package ru.avangard.qa.ibmobile.api;

import ru.avangard.qa.ibmobile.config.Config;

/** Ручки бэка. Версия в конце пути — из versions/&lt;релиз&gt;.properties по имени константы. */
public enum Endpoint {

    AUTH_LOGIN("Auth/ticket", "Логин"),
    AUTH_LOGOUT("Auth/ticket", "Логаут"),
    SEND_CONFIRM_SMS("Auth/send_confirm_sms", "Отправка SMS-кода"),
    SHOW_SCRETCH("Auth/show_scretch", "Запрос кода со скретч-карты"),
    ACC_LIST("Statement/acc_list", "Список счетов"),
    ACC_DETAILS("Statement/acc_details", "Деталка счета"),
    PREPARE_DOC("Doc/prepare_doc", "Подготовка документа", true),
    SEND_DOC("Doc/send_doc", "Отправка документа");

    private final String path;
    private final String title;
    /** prepare_doc и немногие другие; остальные ручки — URLENC. */
    final boolean multipart;

    Endpoint(String path, String title) {
        this(path, title, false);
    }

    Endpoint(String path, String title, boolean multipart) {
        this.path = path;
        this.title = title;
        this.multipart = multipart;
    }

    /** Путь с версией релиза, например Auth/ticket/9.4. */
    String path() {
        return path + "/" + version();
    }

    /** schemas/&lt;ручка&gt;/[&lt;doc_type&gt;/]&lt;версия&gt;.json */
    String schema(DocType docType) {
        return "schemas/" + name().toLowerCase() + "/" + (docType == null ? "" : docType + "/") + version() + ".json";
    }

    private String version() {
        return Config.version(name());
    }

    @Override
    public String toString() {
        return title;
    }
}
