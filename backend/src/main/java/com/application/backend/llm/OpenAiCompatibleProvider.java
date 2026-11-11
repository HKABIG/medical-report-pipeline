package com.application.backend.llm;

import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Any OpenAI-compatible chat completions endpoint (OpenAI, Groq, a local server) with a JSON schema response. */
public class OpenAiCompatibleProvider implements LlmProvider {

    public static final String NAME = "openai";

    private static final String SCHEMA_NAME = "case_extraction";
    private static final String FINISH_STOP = "stop";

    private final String baseUrl;
    private final String model;
    private final Double temperature;

    /** @param temperature null to leave the model's default */
    public OpenAiCompatibleProvider(String baseUrl, String model, Double temperature) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public ProviderCall prepare(LlmRequest request) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("model", model);
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        messages.addObject().put("role", "user").put("content", request.userPrompt());
        ObjectNode format = body.putObject("response_format");
        format.put("type", "json_schema");
        ObjectNode schema = format.putObject("json_schema");
        schema.put("name", SCHEMA_NAME);
        schema.set("schema", JsonSchemas.toJsonSchema(request.responseSchema()));
        return new ProviderCall(URI.create(baseUrl + "/chat/completions"), body);
    }

    @Override
    public Map<String, String> authHeaders(String apiKey) {
        return Map.of("Authorization", "Bearer " + apiKey);
    }

    @Override
    public String extractText(JsonNode responseBody) {
        JsonNode choice = responseBody.path("choices").path(0);
        String finish = choice.path("finish_reason").asText(FINISH_STOP);
        if (!FINISH_STOP.equals(finish)) {
            throw new LlmException("Model stopped with finish_reason " + finish);
        }
        JsonNode content = choice.path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new LlmException("Model returned an empty answer");
        }
        return content.asText();
    }
}
