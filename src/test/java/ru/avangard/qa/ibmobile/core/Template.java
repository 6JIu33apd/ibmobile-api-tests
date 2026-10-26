package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.avangard.qa.ibmobile.api.DocType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

/**
 * doc_content документа для prepare_doc: шаблон templates/&lt;doc_type&gt;.json (копия из Postman), в тесте меняются только нужные поля.
 * <pre>
 * Request.to(PREPARE_DOC).as(s).param("doc_type", DOC_TYPE).param("doc_content", Template.of(DOC_TYPE).put("text", text))
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
    public static ObjectNode set(ObjectNode content, String field, Object value) {
        if (value == null) {
            content.remove(field);
        } else {
            content.putPOJO(field, value);
        }
        return content;
    }

    /** attachmentsList для файлов, которые прикладываются к запросу через Request.file. */
    public static List<Map<String, String>> attachments(List<String> files) {
        return files.stream().map(file -> Map.of("filename", file, "contentType", URLConnection.guessContentTypeFromName(file))).toList();
    }
}
