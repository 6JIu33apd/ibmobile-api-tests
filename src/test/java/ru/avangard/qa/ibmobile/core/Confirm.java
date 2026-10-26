package ru.avangard.qa.ibmobile.core;

import ru.avangard.qa.ibmobile.api.ApiResponse;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.api.Session;
import ru.avangard.qa.ibmobile.config.Config;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_CONFIRM_SMS;
import static ru.avangard.qa.ibmobile.api.Endpoint.SHOW_SCRETCH;

/**
 * Способ подтверждения документа из authMethods ответа prepare_doc (QR не автоматизируется).
 * <pre>
 * Request.to(SEND_DOC).as(s).param("secure", Confirm.NONE.secure(s, prepared)).post()
 * </pre>
 */
public enum Confirm {

    NONE, SMSPWD, SCRETCH;

    /**
     * Значения параметра secure для send_doc. SMSPWD и SCRETCH сначала запрашивают код (send_confirm_sms, show_scretch):
     * без этого код не принимается, а попытки ограничены. Документ ждёт send_doc 5 минут.
     */
    public List<String> secure(Session s, ApiResponse prepared) {
        assertThat(prepared.<List<String>>path("authMethods")).as("способы подтверждения").contains(name());
        return switch (this) {
            case NONE -> List.of(token("NONE", prepared.path("noneAuthInit.code")));
            case SMSPWD -> {
                Request.to(SEND_CONFIRM_SMS).as(s).post().success();
                String code = Config.find("confirm.SMS"); // фиксированный на dev, иначе из БД
                yield List.of(token("SMS", code != null ? code : Db.smsCode(s.login)), token("PWD", s.password));
            }
            case SCRETCH -> {
                Request.to(SHOW_SCRETCH).as(s).post().success();
                yield List.of(token("SCRETCH", Config.get("confirm.SCRETCH")));
            }
        };
    }

    /** secure — JSON-строка; SMSPWD — два параметра secure. */
    private static String token(String method, String token) {
        return "{\"authMethod\":\"" + method + "\",\"authToken\":\"" + token + "\"}";
    }
}
