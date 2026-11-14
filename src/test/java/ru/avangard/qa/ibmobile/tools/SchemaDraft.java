package ru.avangard.qa.ibmobile.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.response.Response;
import org.junit.jupiter.api.extension.*;
import ru.avangard.qa.ibmobile.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Образцы сохраняются до проверок; после прогона обновляются только черновики, включая упавший прогон. */
public final class SchemaDraft implements BeforeAllCallback, AfterEachCallback, AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path SAMPLES = Path.of(".schema-samples");
    private static final ThreadLocal<String> DOC_TYPE = new ThreadLocal<>();

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(ExtensionContext.Namespace.create(SchemaDraft.class)).computeIfAbsent(SchemaDraft.class, key -> this);
    }

    @Override
    public void afterEach(ExtensionContext context) { DOC_TYPE.remove(); }

    /** Неожиданные ответы остаются для разбора, но не расширяют контракт. */
    public static void capture(String method, String path, Map<String, Object> params, Response response) throws IOException {
        String base = path.replaceFirst("/[^/]*$", "").replaceFirst("^/+", "");
        Endpoint endpoint = Arrays.stream(Endpoint.values()).filter(entry -> entry.method.name().equals(method) && entry.basePath.equals(base)).findFirst().orElse(null);
        String docType = Objects.toString(params.get("doc_type"), DOC_TYPE.get());
        if (endpoint == Endpoint.PREPARE_DOC) DOC_TYPE.set(docType);
        boolean document = endpoint == Endpoint.PREPARE_DOC || endpoint == Endpoint.PRIOR_INFO || endpoint == Endpoint.SEND_DOC;
        String body = response.asString(), schema = null;
        try {
            JsonNode json = JSON.readTree(body);
            if (response.statusCode() == 200 && json != null && json.isContainerNode()) {
                if (json.has("errorCode")) {
                    if (json.path("errorCode").isIntegralNumber() && Arrays.stream(ErrorCode.values())
                            .anyMatch(error -> error.errorCode == json.path("errorCode").asInt())) schema = ErrorCode.SCHEMA;
                } else if (json.has("checkResult") && json.path("checkSuccess").isBoolean() && !json.path("checkSuccess").asBoolean()) {
                    schema = "schemas/errors/check_failed.json";
                } else if (endpoint != null && json.path("responseCode").asInt() != -1 && json.path("checkSuccess").asBoolean(true)
                        && (!json.hasNonNull("resultCode") || "SUCCESS".equals(json.path("resultCode").asText()))
                        && !json.path("secureCheck").path("errorExist").asBoolean()
                        && (!document || Arrays.stream(DocType.values()).anyMatch(type -> type.toString().equals(docType)))) {
                    schema = "schemas/" + endpoint.name().toLowerCase() + "/" + (document ? docType + "/" : "")
                            + path.substring(path.lastIndexOf('/') + 1) + ".json";
                }
            }
        } catch (JsonProcessingException ignored) { /* Не-JSON тоже сохраняется. */ }
        Path directory = SAMPLES.resolve(schema == null ? "unexpected/" + (endpoint == null ? "unknown" : endpoint.name()) + "/" + response.statusCode() : schema);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(UUID.nameUUIDFromBytes(body.getBytes(UTF_8)) + (schema == null ? ".txt" : ".json")), body);
    }

    public static void main(String[] args) throws IOException {
        new SchemaDraft().close();
    }

    @Override
    public void close() throws IOException {
        if (Files.notExists(SAMPLES.resolve("schemas"))) return;
        try (var walk = Files.walk(SAMPLES.resolve("schemas"))) {
            for (Path directory : walk.filter(path -> Files.isDirectory(path) && path.toString().endsWith(".json")).toList()) {
                List<JsonNode> samples = new ArrayList<>();
                try (var files = Files.newDirectoryStream(directory, "*.json")) {
                    for (Path file : files) samples.add(JSON.readTree(file.toFile()));
                }
                if (samples.isEmpty()) continue;
                Path draft = Path.of("target/schema-drafts").resolve(SAMPLES.relativize(directory));
                Files.createDirectories(draft.getParent());
                Files.writeString(draft, format(JSON.createObjectNode().put("$schema", "http://json-schema.org/draft-04/schema#")
                        .setAll(infer(samples)), 0) + "\n");
                System.out.println(draft + " <- " + samples.size());
            }
        }
    }

    /** Типы объединяются, обязательны общие поля; дополнительные поля запрещены на каждом уровне. */
    private static ObjectNode infer(List<JsonNode> values) {
        ObjectNode schema = JSON.createObjectNode();
        Set<String> types = values.stream().map(value -> value.isIntegralNumber() ? "integer" : value.getNodeType().name().toLowerCase())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (types.contains("number")) types.remove("integer");
        if (types.size() == 1) schema.put("type", types.iterator().next());
        else types.forEach(schema.putArray("type")::add);
        List<JsonNode> objects = values.stream().filter(JsonNode::isObject).toList();
        if (!objects.isEmpty()) {
            Set<String> fields = new LinkedHashSet<>();
            objects.forEach(object -> object.fieldNames().forEachRemaining(fields::add));
            ObjectNode properties = schema.putObject("properties");
            List<String> required = new ArrayList<>();
            for (String field : fields) {
                List<JsonNode> present = objects.stream().map(object -> object.get(field)).filter(Objects::nonNull).toList();
                properties.set(field, infer(present));
                if (present.size() == objects.size()) required.add(field);
            }
            if (!required.isEmpty()) required.forEach(schema.putArray("required")::add); // draft-04 запрещает пустой required
            schema.put("additionalProperties", false);
        }
        List<JsonNode> items = values.stream().filter(JsonNode::isArray).flatMap(JsonNode::valueStream).toList();
        if (!items.isEmpty()) schema.set("items", infer(items)); // Пустые массивы требуют образца с элементами.
        return schema;
    }

    /** Простые описания полей и списки скаляров остаются в одну строку. */
    private static String format(JsonNode node, int depth) throws IOException {
        if (!node.isContainerNode()) return JSON.writeValueAsString(node);
        List<String> parts = new ArrayList<>();
        if (node.isObject()) {
            for (var field : node.properties()) parts.add(JSON.writeValueAsString(field.getKey()) + " : " + format(field.getValue(), depth + 1));
        } else {
            for (JsonNode item : node) parts.add(format(item, depth + 1));
        }
        String open = node.isObject() ? "{" : "[", close = node.isObject() ? "}" : "]";
        if (parts.isEmpty()) return open + close;
        if (node.valueStream().allMatch(value -> !value.isContainerNode() || value.isArray() && value.valueStream().allMatch(JsonNode::isValueNode)))
            return open + " " + String.join(", ", parts) + " " + close;
        String indent = "  ".repeat(depth);
        return open + "\n" + indent + "  " + String.join(",\n" + indent + "  ", parts) + "\n" + indent + close;
    }
}
