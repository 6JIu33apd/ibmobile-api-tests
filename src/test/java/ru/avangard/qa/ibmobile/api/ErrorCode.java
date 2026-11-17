package ru.avangard.qa.ibmobile.api;

/** Допустимые технические ошибки; остальные коды отклоняет HttpFilter, бизнес-отказы проверяет rejected. */
public enum ErrorCode {
    AUTHORIZATION_REQUIRED(1, "authorization_required"),
    INVALID_PARAMETERS(4, "invalid_parameters"),
    SESSION_EXPIRED(5, "session_expired"),
    WRONG_CREDENTIALS(1000, "Некорректный логин или пароль пользователя."),
    INTERNAL_SERVER_ERROR(2,"internal_server_error");

    public static final String SCHEMA = "schemas/errors/error.json";
    public final int errorCode;
    final String errorDesc;

    ErrorCode(int errorCode, String errorDesc) { this.errorCode = errorCode; this.errorDesc = errorDesc; }
}
