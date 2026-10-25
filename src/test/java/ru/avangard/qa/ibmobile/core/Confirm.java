package ru.avangard.qa.ibmobile.core;

import java.util.List;

import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_CONFIRM_SMS;
import static ru.avangard.qa.ibmobile.api.Endpoint.SHOW_SCRETCH;

/**
 * Подтверждение документа для параметра secure у send_doc.
 * prepare_doc возвращает допустимые способы в authMethods; берётся первый из {@link #PREFERRED}, который бэк предложил.
 * <ul>
 * <li>NONE — код из ответа prepare_doc (noneAuthInit.code);</li>
 * <li>SMS — сначала Auth/send_confirm_sms, код — confirm.SMS из env/&lt;env&gt;.properties, а если его нет, из БД;</li>
 * <li>PWD — пароль пользователя; SMSPWD — два параметра secure: SMS и PWD;</li>
 * <li>SCRETCH — сначала Auth/show_scretch, код — confirm.SCRETCH (есть только на dev, в боевой БД хэш).</li>
 * </ul>
 * QR не поддержан: нужно приложение-подтверждатель.
 */
public final class Confirm {

    private static final List<String> PREFERRED = List.of("NONE", "SMSPWD", "SMS", "PWD", "SCRETCH");

    private Confirm() {
    }

    /** Значения параметра secure (их может быть несколько). */
    public static List<String> secure(Session session, ApiResponse prepared) {
        List<String> offered = prepared.path("authMethods");
        String method = PREFERRED.stream().filter(offered::contains).findFirst()
                .orElseThrow(() -> new IllegalStateException("Не умеем подтверждать способами " + offered));
        return switch (method) {
            case "NONE" -> List.of(secure("NONE", prepared.path("noneAuthInit.code")));
            case "SMSPWD" -> List.of(secure("SMS", smsCode(session)), secure("PWD", session.user().password()));
            case "SMS" -> List.of(secure("SMS", smsCode(session)));
            case "PWD" -> List.of(secure("PWD", session.user().password()));
            default -> List.of(secure("SCRETCH", scretchCode(session)));
        };
    }

    private static String smsCode(Session session) {
        Request.to(SEND_CONFIRM_SMS).as(session).post().success();
        String fixed = Config.find("confirm.SMS");
        return fixed != null ? fixed : Db.smsCode(session.login());
    }

    private static String scretchCode(Session session) {
        String fixed = Config.find("confirm.SCRETCH");
        if (fixed == null) {
            throw new IllegalStateException("Код с карты на контуре " + Config.env() + " получить нельзя (в БД хэш)");
        }
        Request.to(SHOW_SCRETCH).as(session).post().success();
        return fixed;
    }

    private static String secure(String method, String token) {
        return "{\"authMethod\":\"" + method + "\",\"authToken\":\"" + token + "\"}";
    }
}
