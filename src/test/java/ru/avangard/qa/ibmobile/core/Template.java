package ru.avangard.qa.ibmobile.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;

/** doc_content из templates/&lt;doc_type&gt;.json (копия из Postman), в тесте меняются только нужные поля. */
public final class Template {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode json;

    private Template(ObjectNode json) {
        this.json = json;
    }

    public static Template of(String docType) {
        try (var in = Template.class.getResourceAsStream("/templates/" + docType + ".json")) {
            return new Template((ObjectNode) MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
