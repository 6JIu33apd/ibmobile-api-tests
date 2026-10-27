package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.avangard.qa.ibmobile.api.DocType;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * doc_content документа для prepare_doc: шаблон doc_content/&lt;doc_type&gt;.json (копия из Postman), в тесте меняются только нужные поля.
 * <pre>
 * Request.to(PREPARE_DOC).as(session).doc(DOC_TYPE, DocContent.of(DOC_TYPE).put("text", text))
 * </pre>
 */
public final class DocContent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DocContent() {
    }

    public static ObjectNode of(DocType docType) {
        try (var in = DocContent.class.getResourceAsStream("/doc_content/" + docType + ".json")) {
            return (ObjectNode) MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Поле по пути = value, null — без поля (как Request.param для параметров). Значение — строка, число, список, Map.
     * Путь — через точку на любую глубину, индекс массива — число: "amount", "payer.address.city", "attachmentsList.0.filename".
     * Родитель поля должен быть в шаблоне: опечатка в пути роняет тест, а не добавляет лишнее поле.
     */
    public static ObjectNode set(ObjectNode docContent, String path, Object value) {
        JsonPointer pointer = JsonPointer.compile("/" + path.replace('.', '/'));
        var parent = docContent.at(pointer.head());
        var last = pointer.last();
        if (parent instanceof ObjectNode object) {
            if (value == null) {
                object.remove(last.getMatchingProperty());
            } else {
                object.putPOJO(last.getMatchingProperty(), value);
            }
        } else if (parent instanceof ArrayNode array && last.getMatchingIndex() >= 0) {
            if (value == null) {
                array.remove(last.getMatchingIndex());
            } else {
                array.set(last.getMatchingIndex(), array.pojoNode(value));
            }
        } else {
            throw new IllegalArgumentException("Нет поля " + path + " в " + docContent);
        }
        return docContent;
    }
}
