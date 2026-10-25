package ru.avangard.qa.ibmobile.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.avangard.qa.ibmobile.core.ApiResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Черновики JSON-схем из сохранённых ответов.
 * <ol>
 * <li>Прогнать тесты: для ручек без схемы ответы сохранятся в target/schema-samples
 *     (с -Dsamples=true сохраняются все ответы — так собирают образцы под разными пользователями).</li>
 * <li>Запустить main (из IDEA). Схема строится по всем образцам сразу:
 *     поле обязательное, только если оно есть во всех ответах.
 *     Существующие схемы не перезаписываются: чтобы пересоздать черновик, удали файл схемы.</li>
 * <li>Просмотреть черновик в src/test/resources/schemas и поправить required.</li>
 * </ol>
 */
public final class SchemaDraft {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path RESOURCES = Path.of("src", "test", "resources");

    private SchemaDraft() {
    }

    public static void main(String[] args) throws IOException {
        List<Path> dirs;
        try (Stream<Path> walk = Files.walk(ApiResponse.SAMPLES)) {
            dirs = walk.filter(p -> p.getFileName().toString().endsWith(".json") && Files.isDirectory(p)).toList();
        }
        for (Path dir : dirs) {
            Path target = RESOURCES.resolve(ApiResponse.SAMPLES.relativize(dir));
            if (Files.exists(target)) {
                System.out.println("пропущено, схема уже есть: " + target);
                continue;
            }
            List<JsonNode> samples = new ArrayList<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    samples.add(MAPPER.readTree(file.toFile()));
                }
            }
            ObjectNode schema = MAPPER.createObjectNode().put("$schema", "http://json-schema.org/draft-04/schema#");
            schema.setAll(infer(samples));
            Files.createDirectories(target.getParent());
            MAPPER.writeValue(target.toFile(), schema);
            System.out.println("создано по " + samples.size() + " образцам: " + target);
        }
    }

    /** Схема, которой удовлетворяют все значения. */
    static ObjectNode infer(List<JsonNode> values) {
        ObjectNode schema = MAPPER.createObjectNode();
        Set<String> types = new LinkedHashSet<>();
        values.forEach(v -> types.add(type(v)));
        if (types.contains("integer") && types.contains("number")) {
            types.remove("integer");
        }
        if (types.size() == 1) {
            schema.put("type", types.iterator().next());
        } else {
            ArrayNode array = schema.putArray("type");
            types.forEach(array::add);
        }
        List<JsonNode> objects = values.stream().filter(JsonNode::isObject).toList();
        if (!objects.isEmpty()) {
            Set<String> keys = new LinkedHashSet<>();
            objects.forEach(o -> o.fieldNames().forEachRemaining(keys::add));
            ObjectNode properties = schema.putObject("properties");
            ArrayNode required = MAPPER.createArrayNode();
            for (String key : keys) {
                List<JsonNode> fieldValues = objects.stream().filter(o -> o.has(key)).map(o -> o.get(key)).toList();
                properties.set(key, infer(fieldValues));
                if (fieldValues.size() == objects.size()) {
                    required.add(key);
                }
            }
            if (!required.isEmpty()) {
                schema.set("required", required);
            }
            schema.put("additionalProperties", false);
        }
        List<JsonNode> items = new ArrayList<>();
        values.stream().filter(JsonNode::isArray).forEach(a -> a.forEach(items::add));
        if (!items.isEmpty()) {
            schema.set("items", infer(items));
        }
        return schema;
    }

    private static String type(JsonNode value) {
        return switch (value.getNodeType()) {
            case OBJECT -> "object";
            case ARRAY -> "array";
            case STRING -> "string";
            case BOOLEAN -> "boolean";
            case NULL -> "null";
            case NUMBER -> value.isIntegralNumber() ? "integer" : "number";
            default -> throw new IllegalArgumentException("Неожиданный тип: " + value);
        };
    }
}
