package com.application.backend.llm;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Every exchange with the provider for one correlation id, including failed attempts, with credentials removed.
 * {@code responseText} is the answer of the last successful attempt, which replay uses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Fixture(
        @JsonProperty("correlation_id") String correlationId,
        @JsonProperty("provider") String provider,
        @JsonProperty("model") String model,
        @JsonProperty("exchanges") List<Exchange> exchanges,
        @JsonProperty("response_text") String responseText) {

    public Optional<String> answer() {
        return Optional.ofNullable(responseText);
    }

    public String qualifiedModel() {
        return provider + "/" + model;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Exchange(
            @JsonProperty("attempt") int attempt,
            @JsonProperty("url") String url,
            @JsonProperty("request") JsonNode request,
            @JsonProperty("status") Integer status,
            @JsonProperty("response") JsonNode response,
            @JsonProperty("error") String error) {
    }
}
