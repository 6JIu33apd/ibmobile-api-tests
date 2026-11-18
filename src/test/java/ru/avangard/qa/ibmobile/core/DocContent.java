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
    public static void set(ObjectNode content, String path, Object value) {
        JsonPointer pointer = JsonPointer.compile("/" + path.replace('.', '/'));
        switch (content.at(pointer.head())) {
            case ObjectNode object when value == null -> object.remove(pointer.last().getMatchingProperty());
            case ObjectNode object -> object.set(pointer.last().getMatchingProperty(), MAPPER.valueToTree(value));
            case ArrayNode array when value == null -> array.remove(pointer.last().getMatchingIndex());
            case ArrayNode array -> array.set(pointer.last().getMatchingIndex(), MAPPER.valueToTree(value));
            default -> throw new IllegalArgumentException(path);
        }
    }
}
