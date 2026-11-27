package ru.avangard.qa.ibmobile.tools;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.response.Response;
import ru.avangard.qa.ibmobile.api.Endpoint;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static ru.avangard.qa.ibmobile.api.ResponseCheck.success;

public final class SchemaDraft {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Path SAMPLES = Path.of(".schema-samples/samples");

    public static synchronized void capture(Endpoint endpoint, Object docType, Response response) {
        try {
            String body = response.asString();
            JsonNode json = JSON.readTree(body);
            if (json == null || !json.isContainerNode()) return;
            if (json.isObject()) success(response);
            String type = endpoint == Endpoint.PRIOR_INFO ? (String) docType :
                    endpoint == Endpoint.SEND_DOC || endpoint == Endpoint.PREPARE_DOC ? json.at("/doc/docType").textValue() : null;
            save(SAMPLES.resolve(endpoint.name().toLowerCase()).resolve(Objects.toString(type, "")), body);
        } catch (AssertionError ignored) {
        } catch (Exception e) {
            System.err.println("SchemaDraft: " + e);
        }
    }

    public static synchronized void infer() throws IOException {
        if (!Files.exists(SAMPLES)) return;
        try (var files = Files.find(SAMPLES, Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toString().endsWith(".json"))) {
            for (var group : files.collect(java.util.stream.Collectors.groupingBy(Path::getParent)).entrySet()) {
                var samples = new ArrayList<JsonNode>();
                for (Path file : group.getValue()) samples.add(JSON.readTree(file.toFile()));
                save(SAMPLES.resolveSibling("drafts").resolve(SAMPLES.relativize(group.getKey())),
                        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(infer(samples).put("$schema", "http://json-schema.org/draft-04/schema#")));
            }
        }
    }

    private static void save(Path dir, String content) throws IOException {
        Files.writeString(Files.createDirectories(dir).resolve(UUID.nameUUIDFromBytes(content.getBytes()) + ".json"), content);
    }

    private static ObjectNode infer(List<JsonNode> values) {
        var types = new TreeSet<String>();
        var fields = new TreeMap<String, List<JsonNode>>();
        var items = new ArrayList<JsonNode>();
        int objects = 0;
        for (var value : values) {
            types.add(value.isIntegralNumber() ? "integer" : value.getNodeType().name().toLowerCase(Locale.ROOT));
            if (value.isArray()) value.forEach(items::add);
            if (!value.isObject()) continue;
            objects++;
            for (var field : value.properties())
                fields.computeIfAbsent(field.getKey(), k -> new ArrayList<>()).add(field.getValue());
        }
        if (types.contains("number")) types.remove("integer");
        ObjectNode schema = JSON.createObjectNode().set("type", JSON.valueToTree(types.size() == 1 ? types.first() : types));
        if (objects > 0) schema.put("additionalProperties", false);
        for (var field : fields.entrySet()) {
            schema.withObject("properties").set(field.getKey(), infer(field.getValue()));
            if (field.getValue().size() == objects) schema.withArray("required").add(field.getKey());
        }
        if (!items.isEmpty()) schema.set("items", infer(items));
        return schema;
    }

    static void main() throws IOException {
        infer();
    }
}