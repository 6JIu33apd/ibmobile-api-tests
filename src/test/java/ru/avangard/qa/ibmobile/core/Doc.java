package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.avangard.qa.ibmobile.api.ApiResponse;
import ru.avangard.qa.ibmobile.api.Request;
import ru.avangard.qa.ibmobile.api.Session;
import ru.avangard.qa.ibmobile.config.Config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.avangard.qa.ibmobile.api.Endpoint.PREPARE_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_CONFIRM_SMS;
import static ru.avangard.qa.ibmobile.api.Endpoint.SEND_DOC;
import static ru.avangard.qa.ibmobile.api.Endpoint.SHOW_SCRETCH;

/**
 * Документ — всё, что меняет данные: prepare_doc(doc_type) → подтверждение способом из ответа → send_doc.
 * <pre>
 * var prepared = Doc.prepare(s, DOC, Doc.content(DOC).put("text", text), "image.png").success();
 * Doc.send(s, prepared, Confirm.NONE).success();
 * </pre>
 */
public final class Doc {

    /** Способы подтверждения из authMethods ответа prepare_doc (QR не автоматизируется). */
    public enum Confirm { NONE, SMSPWD, SCRETCH }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Doc() {
    }

    /** doc_content из templates/&lt;doc_type&gt;.json (копия из Postman), в тесте меняются только нужные поля. */
    public static ObjectNode content(String docType) {
        try (var in = Doc.class.getResourceAsStream("/templates/" + docType + ".json")) {
            return (ObjectNode) MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Кейс одной строкой параметров: поле field = value, null — без поля. Значение — строка, число, список, Map. */
    public static ObjectNode set(ObjectNode content, String field, Object value) {
        if (value == null) {
            content.remove(field);
        } else {
            content.putPOJO(field, value);
        }
        return content;
    }

    /** Файлы из src/test/resources/files прикладываются к запросу и перечисляются в attachmentsList документа. */
    public static ApiResponse prepare(Session s, String docType, ObjectNode content, String... files) {
        var request = Request.to(PREPARE_DOC).as(s).param("doc_type", docType);
        if (files.length > 0) {
            var attachments = content.putArray("attachmentsList");
            for (String file : files) {
                attachments.addObject().put("filename", file).put("contentType", URLConnection.guessContentTypeFromName(file));
                request.file(file);
            }
        }
        return request.param("doc_content", content).post();
    }

    /** Подтверждение способом confirm (он должен быть в authMethods) и send_doc. Документ ждёт send_doc 5 минут. */
    public static ApiResponse send(Session s, ApiResponse prepared, Confirm confirm) {
        assertThat(prepared.<List<String>>path("authMethods")).as("способы подтверждения").contains(confirm.name());
        List<String> secure = switch (confirm) {
            case NONE -> List.of(secure("NONE", prepared.path("noneAuthInit.code")));
            case SMSPWD -> {
                Request.to(SEND_CONFIRM_SMS).as(s).post().success(); // без отправки код не принимается, а попытки ограничены
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

    /** Параметр secure — JSON-строка; SMSPWD — два параметра secure. */
    private static String secure(String method, String token) {
        return "{\"authMethod\":\"" + method + "\",\"authToken\":\"" + token + "\"}";
    }
}
