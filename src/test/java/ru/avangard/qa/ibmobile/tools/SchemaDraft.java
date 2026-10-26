package ru.avangard.qa.ibmobile.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.avangard.qa.ibmobile.api.ApiResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Черновики схем из target/schema-samples в src/test/resources/schemas (запуск из IDEA).
 * Поле обязательное, если оно есть во всех образцах. Существующие схемы не трогает — для пересоздания удали файл.
 */
public final class SchemaDraft {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws IOException {
        List<Path> dirs;
        try (Stream<Path> walk = Files.walk(ApiResponse.SAMPLES)) {
            dirs = walk.filter(p -> Files.isDirectory(p) && p.toString().endsWith(".json")).toList();
        }
        for (Path dir : dirs) {
            Path target = Path.of("src/test/resources").resolve(ApiResponse.SAMPLES.relativize(dir));
            if (Files.exists(target)) {
                continue;
            }
            List<JsonNode> samples = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    samples.add(MAPPER.readTree(file.toFile()));
                }
            }
            Files.createDirectories(target.getParent());
            MAPPER.writeValue(target.toFile(), MAPPER.createObjectNode()
                    .put("$schema", "http://json-schema.org/draft-04/schema#")
                    .setAll(infer(samples)));
            System.out.println(target + " <- " + samples.size());
        }
    }

    private static ObjectNode infer(List<JsonNode> values) {
        ObjectNode schema = MAPPER.createObjectNode();
        Set<String> types = new LinkedHashSet<>();
        values.forEach(v -> types.add(v.isIntegralNumber() ? "integer" : v.getNodeType().name().toLowerCase()));
        if (types.remove("integer")) {
            types.add(types.contains("number") ? "number" : "integer");
        }
        if (types.size() == 1) {
            schema.put("type", types.iterator().next());
        } else {
            types.forEach(schema.putArray("type")::add);
        }
        List<JsonNode> objects = values.stream().filter(JsonNode::isObject).toList();
        if (!objects.isEmpty()) {
            Set<String> keys = new LinkedHashSet<>();
            objects.forEach(o -> o.fieldNames().forEachRemaining(keys::add));
            ObjectNode properties = schema.putObject("properties");
            var required = MAPPER.createArrayNode();
            for (String key : keys) {
                List<JsonNode> fieldValues = objects.stream().filter(o -> o.has(key)).map(o -> o.get(key)).toList();
                properties.set(key, infer(fieldValues));
                if (fieldValues.size() == objects.size()) {
                    required.add(key);
                }
            }
            if (!required.isEmpty()) { // draft-04 не допускает пустой required
                schema.set("required", required);
            }
            schema.put("additionalProperties", false);
        }
        List<JsonNode> items = values.stream().filter(JsonNode::isArray).flatMap(a -> a.valueStream()).toList();
        if (!items.isEmpty()) {
            schema.set("items", infer(items));
        }
        return schema;
    }
}
