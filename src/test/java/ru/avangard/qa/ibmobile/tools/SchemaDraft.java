package ru.avangard.qa.ibmobile.tools;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.api.Endpoint;

import java.nio.file.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static ru.avangard.qa.ibmobile.api.ResponseCheck.success;

public final class SchemaDraft {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static synchronized void capture(Endpoint endpoint, Object docType, Response response) {
        if (Boolean.getBoolean("schemaDraft.skip")) return;
        try {
            String body = response.asString();
            JsonNode json = JSON.readTree(body);
            if (!json.isContainerNode()) return;
            if (json.isObject()) success(response);
            String path = endpoint.name().toLowerCase() + (docType == null ? "" : "/" + docType);
            Path directory = Files.createDirectories(Path.of(".schema-samples/schemas", path));
            String name = UUID.nameUUIDFromBytes(body.getBytes(UTF_8)) + ".json";
            Files.writeString(directory.resolve(name), body);
            List<JsonNode> samples = new ArrayList<>(List.of(json));
            for (var file : directory.toFile().listFiles(file -> file.isFile() && file.getName().endsWith(".json")))
                if (!name.equals(file.getName())) samples.add(JSON.readTree(file));
            JSON.writerWithDefaultPrettyPrinter().writeValue(Files.createDirectories(Path.of(".schema-samples/drafts/schemas", path)).resolve(name).toFile(),
                    JSON.createObjectNode().put("$schema", "http://json-schema.org/draft-04/schema#").setAll(infer(samples)));
        } catch (Exception | AssertionError ignored) {}
    }

    private static ObjectNode infer(List<JsonNode> values) {
        ObjectNode schema = JSON.createObjectNode();
        Set<String> types = new LinkedHashSet<>(values.stream()
                .map(value -> value.isIntegralNumber() ? "integer" : value.getNodeType().name().toLowerCase()).toList());
        if (types.contains("number")) types.remove("integer");
        schema.set("type", JSON.valueToTree(types.size() == 1 ? types.iterator().next() : types));
        List<JsonNode> objects = values.stream().filter(JsonNode::isObject).toList();
        for (String field : objects.stream().flatMap(object -> object.properties().stream()).map(Map.Entry::getKey).distinct().toList()) {
            List<JsonNode> present = objects.stream().map(object -> object.get(field)).filter(Objects::nonNull).toList();
            schema.withObject("properties").set(field, infer(present));
            if (present.size() == objects.size()) schema.withArray("required").add(field);
        }
        if (!objects.isEmpty()) schema.put("additionalProperties", false);
        List<JsonNode> items = values.stream().filter(JsonNode::isArray).flatMap(JsonNode::valueStream).toList();
        if (!items.isEmpty()) schema.set("items", infer(items)); // Пустые массивы требуют образца с элементами.
        return schema;
    }
}
