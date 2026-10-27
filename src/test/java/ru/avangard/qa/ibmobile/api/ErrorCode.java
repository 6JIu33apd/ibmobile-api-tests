package ru.avangard.qa.ibmobile.api;

import java.util.Arrays;

/**
 * Допустимые технические ошибки с errorCode; любой другой errorCode — ошибка сервера и роняет тест.
 * Бизнес-ошибки приходят отказом проверок (checkResult с badItem) — см. {@link ApiResponse#rejected}.
 * Слои бэка отвечают в разных форматах — у каждой ошибки своя схема из schemas/errors.
 */
public enum ErrorCode {

    AUTHORIZATION_REQUIRED(1, "authorization_required", "error"),
    INVALID_PARAMETERS(4, "invalid_parameters", "error"),
    SESSION_EXPIRED(5, "session_expired", "error_result"),
    WRONG_CREDENTIALS(1000, "Некорректный логин или пароль пользователя.", "error");

    final int errorCode;
    final String errorDesc;
    final String schema;

    ErrorCode(int errorCode, String errorDesc, String schema) {
        this.errorCode = errorCode;
        this.errorDesc = errorDesc;
        this.schema = "schemas/errors/" + schema + ".json";
    }

    /** Остальные коды Request считает ошибкой сервера. */
    static boolean allowed(int errorCode) {
        return Arrays.stream(values()).anyMatch(error -> error.errorCode == errorCode);
    }
}
