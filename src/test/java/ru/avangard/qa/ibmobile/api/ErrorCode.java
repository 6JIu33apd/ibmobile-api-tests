package ru.avangard.qa.ibmobile.api;

public enum ErrorCode {
    AUTHORIZATION_REQUIRED(1, "authorization_required"),
    INVALID_PARAMETERS(4, "invalid_parameters"),
    SESSION_EXPIRED(5, "session_expired"),
    WRONG_CREDENTIALS(1000, "Некорректный логин или пароль пользователя.");

    public final int errorCode;
    final String errorDesc;

    ErrorCode(int errorCode, String errorDesc) { this.errorCode = errorCode; this.errorDesc = errorDesc; }
}
