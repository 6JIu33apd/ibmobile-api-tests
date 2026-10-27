package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Request.to(SEND_DOC).as(session).param("secure", AuthMethod.NONE.secure(session, prepared)).post()
 * </pre>
 */
public enum AuthMethod {

    NONE, SMSPWD, SCRETCH;

    /**
     * Значения параметра secure для send_doc. SMSPWD и SCRETCH сначала запрашивают код (send_confirm_sms, show_scretch):
     * без этого код не принимается, а попытки ограничены. Документ ждёт send_doc 5 минут.
     */
    public List<ObjectNode> secure(Session session, ApiResponse prepared) {
        assertThat(prepared.<List<String>>path("authMethods")).as("способы подтверждения").contains(name());
        return switch (this) {
            case NONE -> List.of(secure("NONE", prepared.path("noneAuthInit.code")));
            case SMSPWD -> {
                Request.to(SEND_CONFIRM_SMS).as(session).post().success();
                String smsCode = Config.find("confirm.SMS"); // фиксированный на dev, иначе из БД
                yield List.of(secure("SMS", smsCode != null ? smsCode : Db.smsCode(session.login)), secure("PWD", session.password));
            }
            case SCRETCH -> {
                Request.to(SHOW_SCRETCH).as(session).post().success();
                yield List.of(secure("SCRETCH", Config.get("confirm.SCRETCH")));
            }
        };
    }

    /** secure — JSON-строка (Jackson экранирует " и \ в пароле); SMSPWD — два параметра secure. */
    private static ObjectNode secure(String authMethod, String authToken) {
        return JsonNodeFactory.instance.objectNode().put("authMethod", authMethod).put("authToken", authToken);
    }
}
