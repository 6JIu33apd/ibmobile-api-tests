package ru.avangard.qa.ibmobile.core;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    /** Способы подтверждения из authMethods ответа prepare_doc (QR не автоматизируется). */
    public enum Confirm { NONE, SMSPWD, SCRETCH }

    private Doc() {
    }

    /** Файлы из src/test/resources/files прикладываются частями file и перечисляются в attachmentsList документа. */
    public static ApiResponse prepare(Session s, String docType, Template content, String... files) {
        var request = Request.to(PREPARE_DOC).as(s).param("doc_type", docType);
        if (files.length > 0) {
            content.with("attachmentsList", Stream.of(files)
                    .map(file -> Map.of("filename", file, "contentType", Request.contentType(file)))
                    .toList());
            Stream.of(files).forEach(request::file);
        }
        return request.param("doc_content", content).post();
    }

    public static ApiResponse send(Session s, ApiResponse prepared, Confirm confirm) {
        assertThat(prepared.<List<String>>path("authMethods")).as("способы подтверждения").contains(confirm.name());
        List<String> secure = switch (confirm) {
            case NONE -> List.of(secure("NONE", prepared.path("noneAuthInit.code")));
            case SMSPWD -> {
                Request.to(SEND_CONFIRM_SMS).as(s).post().success();
                String code = Config.find("confirm.SMS"); // фиксированный на dev, иначе из БД
                yield List.of(secure("SMS", code != null ? code : Db.smsCode(s.login)), secure("PWD", s.password));
            }
            case SCRETCH -> {
                Request.to(SHOW_SCRETCH).as(s).post().success();
                yield List.of(secure("SCRETCH", Config.get("confirm.SCRETCH")));
            }
        };
        return Request.to(SEND_DOC).as(s).param("secure", secure).post();
    }

    private static String secure(String method, String token) {
        return "{\"authMethod\":\"" + method + "\",\"authToken\":\"" + token + "\"}";
    }
}
