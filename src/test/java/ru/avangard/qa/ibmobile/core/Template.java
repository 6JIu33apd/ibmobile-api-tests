package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;

/**
 * doc_content документа: шаблон templates/&lt;doc_type&gt;.json (можно скопировать из Postman),
 * в тесте меняются только нужные поля.
 * <pre>
 * Template.of("ib_our_acc_transfer").with("amount", 1.0).with("accDeb", from)
 * </pre>
 */
public final class Template {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode json;

    private Template(ObjectNode json) {
        this.json = json;
    }

    public static Template of(String docType) {
        String name = "templates/" + docType + ".json";
        try (InputStream in = Template.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalArgumentException("Нет шаблона " + name);
            }
            return new Template((ObjectNode) MAPPER.readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + name, e);
        }
    }

    public Template with(String field, Object value) {
        json.set(field, MAPPER.valueToTree(value));
        return this;
    }

    public Template without(String field) {
        json.remove(field);
        return this;
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
