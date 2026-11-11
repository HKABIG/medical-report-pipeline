package com.application.backend.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Converts the provider-neutral response schema into each provider's dialect. */
final class JsonSchemas {

    static final String TYPE = "type";
    static final String NULLABLE = "nullable";
    static final String PROPERTIES = "properties";
    static final String ITEMS = "items";
    static final String ENUM = "enum";
    static final String REQUIRED = "required";
    static final String DESCRIPTION = "description";

    private JsonSchemas() {
    }

    /** Gemini's OpenAPI subset: upper-case types, explicit property order, enum strings marked as such. */
    static JsonNode toGemini(JsonNode schema) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> entry : schema.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            switch (key) {
                case TYPE -> out.put(TYPE, value.asText().toUpperCase());
                case PROPERTIES -> {
                    ObjectNode properties = out.putObject(PROPERTIES);
                    ArrayNode order = JsonNodeFactory.instance.arrayNode();
                    for (Map.Entry<String, JsonNode> property : value.properties()) {
                        properties.set(property.getKey(), toGemini(property.getValue()));
                        order.add(property.getKey());
                    }
                    out.set("propertyOrdering", order);
                }
                case ITEMS -> out.set(ITEMS, toGemini(value));
                default -> out.set(key, value.deepCopy());
            }
        }
        if (out.has(ENUM)) {
            out.put("format", "enum");
        }
        return out;
    }

    /** Standard JSON Schema: nullability as a type union, closed objects. */
    static JsonNode toJsonSchema(JsonNode schema) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        boolean nullable = schema.path(NULLABLE).asBoolean(false);
        for (Map.Entry<String, JsonNode> entry : schema.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            switch (key) {
                case NULLABLE -> {
                }
                case TYPE -> {
                    if (nullable) {
                        out.putArray(TYPE).add(value.asText()).add("null");
                    } else {
                        out.set(TYPE, value.deepCopy());
                    }
                }
                case PROPERTIES -> {
                    ObjectNode properties = out.putObject(PROPERTIES);
                    for (Map.Entry<String, JsonNode> property : value.properties()) {
                        properties.set(property.getKey(), toJsonSchema(property.getValue()));
                    }
                    out.put("additionalProperties", false);
                }
                case ITEMS -> out.set(ITEMS, toJsonSchema(value));
                case ENUM -> {
                    ArrayNode values = out.putArray(ENUM);
                    value.forEach(values::add);
                    if (nullable) {
                        values.addNull();
                    }
                }
                default -> out.set(key, value.deepCopy());
            }
        }
        return out;
    }

    static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }
}
