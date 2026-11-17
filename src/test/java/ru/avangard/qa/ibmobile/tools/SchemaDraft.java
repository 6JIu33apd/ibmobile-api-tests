package ru.avangard.qa.ibmobile.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import io.restassured.response.Response;
import org.junit.jupiter.api.extension.*;
import ru.avangard.qa.ibmobile.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class SchemaDraft implements AfterEachCallback {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path SAMPLES = Path.of(".schema-samples");
    private static final ThreadLocal<DocType> DOC_TYPE = new ThreadLocal<>();

    @Override
    public void afterEach(ExtensionContext context) { DOC_TYPE.remove(); }

    public static synchronized void capture(String method, String path, Map<String, Object> params, Response response) throws IOException, ProcessingException {
        if (Boolean.getBoolean("schemaDraft.skip") || response.statusCode() != 200) return;
        String base = path.replaceFirst("/[^/]*$", "").replaceFirst("^/+", "");
        Endpoint endpoint = Arrays.stream(Endpoint.values()).filter(entry -> entry.method.name().equals(method) && entry.basePath.equals(base)).findFirst().orElse(null);
        DocType docType = params.get("doc_type") == null ? DOC_TYPE.get() : Arrays.stream(DocType.values())
                .filter(type -> type.toString().equals(params.get("doc_type").toString())).findFirst().orElse(null);
        if (endpoint == Endpoint.PREPARE_DOC) DOC_TYPE.set(docType);
        JsonNode json = JSON.readTree(response.asString());
        String body = response.asString(), schema = schema(json, endpoint, docType, path);
        if (schema == null) return;
        boolean unexpected = extraFields(schema, json);
        Path directory = SAMPLES.resolve(unexpected ? "unexpected/" + endpoint.name() + "/200" : schema);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(UUID.nameUUIDFromBytes(body.getBytes(UTF_8)) + ".json"), body);
        if (!unexpected) update(schema, directory);
    }

    private static String schema(JsonNode json, Endpoint endpoint, DocType docType, String path) {
        if (json == null || !json.isContainerNode() || json.has("errorCode") || endpoint == null
                || json.path("responseCode").asInt() == -1 || !json.path("checkSuccess").asBoolean(true)
                || !"SUCCESS".equals(json.path("resultCode").asText("SUCCESS")) || json.path("secureCheck").path("errorExist").asBoolean()) return null;
        boolean document = endpoint == Endpoint.PREPARE_DOC || endpoint == Endpoint.PRIOR_INFO || endpoint == Endpoint.SEND_DOC;
        if (document && docType == null) return null;
        return "schemas/" + endpoint.name().toLowerCase() + "/" + (document ? docType + "/" : "")
                + path.substring(path.lastIndexOf('/') + 1) + ".json";
    }

    private static boolean extraFields(String schema, JsonNode json) throws ProcessingException {
        var resource = SchemaDraft.class.getResource("/" + schema);
        if (resource != null)
            for (var message : JsonSchemaFactory.byDefault().getJsonSchema(resource.toString()).validate(json, true))
                if (extra(message.asJson())) return true;
        return false;
    }

    private static boolean extra(JsonNode report) {
        if (report.isArray()) return report.valueStream().anyMatch(SchemaDraft::extra);
        // В oneOf поле лишнее, только если его запрещают все ветки.
        JsonNode reports = report.path("reports");
        return "additionalProperties".equals(report.path("keyword").asText())
                || reports.isObject() && !reports.isEmpty() && reports.valueStream().allMatch(SchemaDraft::extra);
    }

    private static void update(String schema, Path directory) throws IOException {
        List<JsonNode> samples = new ArrayList<>();
        try (var files = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : files) samples.add(JSON.readTree(file.toFile()));
        }
        Path draft = SAMPLES.resolve("drafts").resolve(schema);
        Files.createDirectories(draft.getParent());
        Files.writeString(draft, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.createObjectNode()
                .put("$schema", "http://json-schema.org/draft-04/schema#").setAll(infer(samples))) + "\n");
    }

    private static ObjectNode infer(List<JsonNode> values) {
        ObjectNode schema = JSON.createObjectNode();
        Set<String> types = new LinkedHashSet<>(values.stream()
                .map(value -> value.isIntegralNumber() ? "integer" : value.getNodeType().name().toLowerCase()).toList());
        if (types.contains("number")) types.remove("integer");
        schema.set("type", JSON.valueToTree(types.size() == 1 ? types.iterator().next() : types));
        List<JsonNode> objects = values.stream().filter(JsonNode::isObject).toList();
        if (!objects.isEmpty()) properties(schema, objects);
        List<JsonNode> items = values.stream().filter(JsonNode::isArray).flatMap(JsonNode::valueStream).toList();
        if (!items.isEmpty()) schema.set("items", infer(items)); // Пустые массивы требуют образца с элементами.
        return schema;
    }

    private static void properties(ObjectNode schema, List<JsonNode> objects) {
        ObjectNode properties = schema.putObject("properties");
        for (String field : objects.stream().flatMap(object -> object.properties().stream()).map(Map.Entry::getKey).distinct().toList()) {
            List<JsonNode> present = objects.stream().map(object -> object.get(field)).filter(Objects::nonNull).toList();
            properties.set(field, infer(present));
            if (present.size() == objects.size()) schema.withArray("required").add(field);
        }
        schema.put("additionalProperties", false);
    }
}
