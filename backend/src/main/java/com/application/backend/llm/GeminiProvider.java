package com.application.backend.llm;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Google Gemini generateContent with a response schema. The key travels in a header, never in the URL. */
public class GeminiProvider implements LlmProvider {

    public static final String NAME = "gemini";

    private static final String KEY_HEADER = "x-goog-api-key";
    private static final String FINISH_STOP = "STOP";
    private static final String RETRY_INFO_TYPE = "type.googleapis.com/google.rpc.RetryInfo";
    private static final String QUOTA_FAILURE_TYPE = "type.googleapis.com/google.rpc.QuotaFailure";
    private static final String PER_DAY = "PerDay";
    private static final Pattern SECONDS = Pattern.compile("^(\\d+(?:\\.\\d+)?)s$");
    private static final int MAX_OUTPUT_TOKENS = 32768;

    private final String baseUrl;
    private final String model;
    private final Double temperature;
    private final String thinkingLevel;

    /**
     * @param temperature   null to leave the model's default
     * @param thinkingLevel null to leave the model's default
     */
    public GeminiProvider(String baseUrl, String model, Double temperature, String thinkingLevel) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.thinkingLevel = thinkingLevel == null || thinkingLevel.isBlank() ? null : thinkingLevel;
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
        JsonNodeFactory json = JsonNodeFactory.instance;
        ObjectNode body = json.objectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", request.systemPrompt());
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", request.userPrompt());
        ObjectNode config = body.putObject("generationConfig");
        if (temperature != null) {
            config.put("temperature", temperature);
        }
        config.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
        if (thinkingLevel != null) {
            config.putObject("thinkingConfig").put("thinkingLevel", thinkingLevel);
        }
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", JsonSchemas.toGemini(request.responseSchema()));
        return new ProviderCall(URI.create(baseUrl + "/models/" + model + ":generateContent"), body);
    }

    @Override
    public Map<String, String> authHeaders(String apiKey) {
        return Map.of(KEY_HEADER, apiKey);
    }

    @Override
    public String extractText(JsonNode responseBody) {
        JsonNode candidate = responseBody.path("candidates").path(0);
        if (candidate.isMissingNode()) {
            throw new LlmException("Gemini returned no candidate: " + responseBody.path("promptFeedback"));
        }
        String finish = candidate.path("finishReason").asText(FINISH_STOP);
        if (!FINISH_STOP.equals(finish)) {
            throw new LlmException("Gemini stopped with finishReason " + finish);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.has("text")) {
                text.append(part.get("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new LlmException("Gemini returned an empty answer");
        }
        return text.toString();
    }

    @Override
    public Optional<Duration> retryDelay(JsonNode errorBody) {
        for (JsonNode detail : errorBody.path("error").path("details")) {
            if (RETRY_INFO_TYPE.equals(detail.path("@type").asText())) {
                Matcher matcher = SECONDS.matcher(detail.path("retryDelay").asText());
                if (matcher.matches()) {
                    long millis = Math.round(Double.parseDouble(matcher.group(1)) * 1000);
                    return Optional.of(Duration.ofMillis(millis));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean isDailyQuotaExhausted(JsonNode errorBody) {
        for (JsonNode detail : errorBody.path("error").path("details")) {
            if (QUOTA_FAILURE_TYPE.equals(detail.path("@type").asText())) {
                for (JsonNode violation : detail.path("violations")) {
                    if (violation.path("quotaId").asText().contains(PER_DAY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
