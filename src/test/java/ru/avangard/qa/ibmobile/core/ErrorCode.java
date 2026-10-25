package ru.avangard.qa.ibmobile.core;

/** Бизнес-ошибки. Слои бэка отвечают в разных форматах — у каждой ошибки своя схема из schemas/errors. */
public enum ErrorCode {

    AUTHORIZATION_REQUIRED(1, "authorization_required", "error"),
    INVALID_PARAMETERS(4, "invalid_parameters", "error"),
    SESSION_EXPIRED(5, "session_expired", "error_result");

    final int code;
    final String desc;
    final String schema;

    ErrorCode(int code, String desc, String schema) {
        this.code = code;
        this.desc = desc;
        this.schema = "schemas/errors/" + schema + ".json";
    }
}
