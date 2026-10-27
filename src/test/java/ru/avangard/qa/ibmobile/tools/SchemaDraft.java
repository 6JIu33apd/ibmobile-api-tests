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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Черновики схем из target/schema-samples в src/test/resources/schemas (запуск из IDEA).
 * Поле обязательное, если оно есть во всех образцах. Существующие схемы не трогает — для пересоздания удали файл.
 */
public final class SchemaDraft {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Образцы схемы лежат в папке с её именем: schema-samples/acc_list/4.0.json/*.json → schemas/acc_list/4.0.json. */
    public static void main(String[] args) throws IOException {
        List<Path> directories;
        try (Stream<Path> walk = Files.walk(ApiResponse.SAMPLES)) {
            directories = walk.filter(path -> Files.isDirectory(path) && path.toString().endsWith(".json")).toList();
        }
        for (Path directory : directories) {
            Path schema = Path.of("src/test/resources").resolve(ApiResponse.SAMPLES.relativize(directory));
            if (Files.notExists(schema)) {
                List<JsonNode> samples = samples(directory);
                Files.createDirectories(schema.getParent());
                MAPPER.writeValue(schema.toFile(), MAPPER.createObjectNode()
                        .put("$schema", "http://json-schema.org/draft-04/schema#")
                        .setAll(infer(samples)));
                System.out.println(schema + " <- " + samples.size());
            }
        }
    }

    /** Ответы из папки образцов. */
    private static List<JsonNode> samples(Path directory) throws IOException {
        List<JsonNode> samples = new ArrayList<>();
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                samples.add(MAPPER.readTree(file.toFile()));
            }
        }
        return samples;
    }

    /** Схема, которой соответствуют все значения: тип, поля объектов, элементы массивов (рекурсивно). */
    private static ObjectNode infer(List<JsonNode> values) {
        ObjectNode schema = MAPPER.createObjectNode();
        type(schema, values);
        List<JsonNode> objects = values.stream().filter(JsonNode::isObject).toList();
        if (!objects.isEmpty()) {
            properties(schema, objects);
        }
        List<JsonNode> items = values.stream().filter(JsonNode::isArray).flatMap(JsonNode::valueStream).toList();
        if (!items.isEmpty()) {
            schema.set("items", infer(items));
        }
        return schema;
    }

    /** type: один или список, если значения разных типов (например, строка и null); целые вместе с дробными — number. */
    private static void type(ObjectNode schema, List<JsonNode> values) {
        Set<String> types = values.stream()
                .map(value -> value.isIntegralNumber() ? "integer" : value.getNodeType().name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (types.contains("number")) {
            types.remove("integer");
        }
        if (types.size() == 1) {
            schema.put("type", types.iterator().next());
        } else {
            types.forEach(schema.putArray("type")::add);
        }
    }

    /** properties всех полей объектов; required — поля, которые есть в каждом объекте; лишние поля запрещены. */
    private static void properties(ObjectNode schema, List<JsonNode> objects) {
        Set<String> fields = new LinkedHashSet<>();
        objects.forEach(object -> object.fieldNames().forEachRemaining(fields::add));
        ObjectNode properties = schema.putObject("properties");
        List<String> required = new ArrayList<>();
        for (String field : fields) {
            List<JsonNode> values = objects.stream().map(object -> object.get(field)).filter(Objects::nonNull).toList();
            properties.set(field, infer(values));
            if (values.size() == objects.size()) {
                required.add(field);
            }
        }
        if (!required.isEmpty()) { // draft-04 не допускает пустой required
            required.forEach(schema.putArray("required")::add);
        }
        schema.put("additionalProperties", false);
    }
}
