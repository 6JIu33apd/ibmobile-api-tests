package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.specification.RequestSpecification;
import ru.avangard.qa.ibmobile.api.DocType;
import java.io.*;
import java.net.URLConnection;

/** Шаблон doc_content, правка полей и вложения до отправки. */
public final class DocContent {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private DocContent() {}

    public static ObjectNode load(DocType docType) {
        try (var in = DocContent.class.getResourceAsStream("/doc_content/" + docType + ".json")) {
            return (ObjectNode) MAPPER.readTree(in);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Без файлов оставляет attachmentsList из кейса, в том числе негативного. */
    public static void attach(RequestSpecification request, ObjectNode content, String... names) {
        if (names.length == 0) return;
        var attachments = content.putArray("attachmentsList");
        for (String name : names) {
            String type = URLConnection.guessContentTypeFromName(name);
            try (var in = DocContent.class.getResourceAsStream("/files/" + name)) {
                request.multiPart("file", name, in.readAllBytes(), type);
                attachments.addObject().put("filename", name).put("contentType", type);
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }

    /** Путь через точку, индексы массива — числа; null удаляет поле или элемент. */
    public static ObjectNode set(ObjectNode content, String path, Object value) {
        JsonPointer pointer = JsonPointer.compile("/" + path.replace('.', '/'));
        var parent = content.at(pointer.head());
        var last = pointer.last();
        if (parent instanceof ObjectNode object) {
            if (value == null) object.remove(last.getMatchingProperty());
            else object.set(last.getMatchingProperty(), MAPPER.valueToTree(value));
        } else {
            ArrayNode array = (ArrayNode) parent;
            if (value == null) array.remove(last.getMatchingIndex());
            else array.set(last.getMatchingIndex(), MAPPER.valueToTree(value));
        }
        return content;
    }
}
