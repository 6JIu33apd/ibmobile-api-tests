package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.avangard.qa.ibmobile.api.DocType;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * doc_content документа для prepare_doc: шаблон templates/&lt;doc_type&gt;.json (копия из Postman), в тесте меняются только нужные поля.
 * <pre>
 * Request.to(PREPARE_DOC).as(session).doc(DOC_TYPE, Template.of(DOC_TYPE).put("text", text))
 * </pre>
 */
public final class Template {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Template() {
    }

    public static ObjectNode of(DocType docType) {
        try (var in = Template.class.getResourceAsStream("/templates/" + docType + ".json")) {
            return (ObjectNode) MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Поле field = value, null — без поля (как Request.param для параметров). Значение — строка, число, список, Map. */
    public static ObjectNode set(ObjectNode docContent, String field, Object value) {
        if (value == null) {
            docContent.remove(field);
        } else {
            docContent.putPOJO(field, value);
        }
        return docContent;
    }
}
