package ru.avangard.qa.ibmobile.core;

/**
 * Известные бизнес-ошибки. Разные слои бэка отвечают в разных форматах,
 * поэтому у каждой ошибки указан шаблон схемы из schemas/errors.
 */
public enum ErrorCode {

    AUTHORIZATION_REQUIRED(1, "authorization_required", "errors/error.json"),
    INVALID_PARAMETERS(4, "invalid_parameters", "errors/error.json"),
    SESSION_EXPIRED(5, "session_expired", "errors/error_result.json");

    private final int code;
    private final String desc;
    private final String schema;

    ErrorCode(int code, String desc, String schema) {
        this.code = code;
        this.desc = desc;
        this.schema = "schemas/" + schema;
    }

    public int code() {
        return code;
    }

    public String desc() {
        return desc;
    }

    public String schema() {
        return schema;
    }
}
