package com.application.backend.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.application.backend.TestSupport;
import com.fasterxml.jackson.databind.JsonNode;

class JsonSchemasTest {

    private final JsonNode neutral = read("""
            {"type":"object","properties":{"b":{"type":"string","enum":["x","y"],"nullable":true},
             "a":{"type":"array","items":{"type":"integer"}}},"required":["b","a"]}""");

    private static JsonNode read(String json) {
        try {
            return TestSupport.JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void geminiGetsUpperCaseTypesAndThePropertyOrderOfTheCatalog() {
        JsonNode gemini = JsonSchemas.toGemini(neutral);
        assertThat(gemini.path("type").asText()).isEqualTo("OBJECT");
        assertThat(gemini.path("propertyOrdering")).extracting(JsonNode::asText).containsExactly("b", "a");
        assertThat(gemini.path("properties").path("b").path("format").asText()).isEqualTo("enum");
        assertThat(gemini.path("properties").path("a").path("items").path("type").asText()).isEqualTo("INTEGER");
    }

    @Test
    void openAiGetsNullableAsATypeUnion() {
        JsonNode schema = JsonSchemas.toJsonSchema(neutral);
        assertThat(schema.path("properties").path("b").path("type")).extracting(JsonNode::asText)
                .containsExactly("string", "null");
        assertThat(schema.path("properties").path("b").has("nullable")).isFalse();
        assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
    }
}
