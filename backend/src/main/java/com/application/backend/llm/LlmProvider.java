package com.application.backend.llm;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/** Wire format of one provider. Transport, retries, rate limiting and recording live in {@link HttpLlmClient}. */
public interface LlmProvider {

    String name();

    String model();

    ProviderCall prepare(LlmRequest request);

    /** Headers that carry the API key. They are never recorded. */
    Map<String, String> authHeaders(String apiKey);

    /** The answer text of a successful response, or an exception when the response carries no usable answer. */
    String extractText(JsonNode responseBody);

    /** A delay the provider asked for in an error body, when it gives one. */
    default Optional<Duration> retryDelay(JsonNode errorBody) {
        return Optional.empty();
    }

    /** Whether an error says a daily quota is exhausted, as opposed to a per-minute limit. */
    default boolean isDailyQuotaExhausted(JsonNode errorBody) {
        return false;
    }

    default String qualifiedModel() {
        return name() + "/" + model();
    }
}
