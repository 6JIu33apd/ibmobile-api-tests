package ru.avangard.qa.ibmobile.core;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.core.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.core.Endpoint.SEND_CONFIRM_SMS;
import static ru.avangard.qa.ibmobile.core.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.core.Endpoint.SHOW_SCRETCH;

/**
 * Документ — всё, что меняет данные: prepare_doc(doc_type) → подтверждение способом из ответа → send_doc.
 * <pre>
 * var prepared = Doc.prepare(s, "ib_our_acc_transfer", content).success();
 * Doc.send(s, prepared, Confirm.NONE).success();
 * </pre>
 */
public final class Doc {

    /** Способы подтверждения из authMethods ответа prepare_doc. */
    public enum Confirm { NONE, SMS, PWD, SMSPWD, SCRETCH }

    private Doc() {
    }

    public static ApiResponse prepare(Session s, String docType, Object content, String... files) {
        var request = Request.to(PREPARE_DOC).as(s).param("doc_type", docType).param("doc_content", content);
        for (String file : files) {
            request.file(file);
        }
        return request.post();
    }

    public static ApiResponse send(Session s, ApiResponse prepared, Confirm confirm) {
        assertThat(prepared.<List<String>>path("authMethods")).as("способы подтверждения").contains(confirm.name());
        List<String> secure = switch (confirm) {
            case NONE -> List.of(secure("NONE", prepared.path("noneAuthInit.code")));
            case SMS -> List.of(sms(s));
            case PWD -> List.of(secure("PWD", s.password));
            case SMSPWD -> List.of(sms(s), secure("PWD", s.password));
            case SCRETCH -> {
                Request.to(SHOW_SCRETCH).as(s).post().success();
                yield List.of(secure("SCRETCH", Config.get("confirm.SCRETCH")));
            }
        };
        return Request.to(SEND_DOC).as(s).param("secure", secure).post();
    }

    /** Код SMS: фиксированный confirm.SMS контура (dev), иначе из БД. */
    private static String sms(Session s) {
        Request.to(SEND_CONFIRM_SMS).as(s).post().success();
        String fixed = Config.find("confirm.SMS");
        return secure("SMS", fixed != null ? fixed : Db.smsCode(s.login));
    }

    private static String secure(String method, String token) {
        return "{\"authMethod\":\"" + method + "\",\"authToken\":\"" + token + "\"}";
    }
}
