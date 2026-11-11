package com.application.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One structured-output request.
 *
 * @param correlationId  identifies the recording, e.g. the document id; independent of prompt and catalog so a
 *                       changed catalog can still be replayed against the same recording
 * @param responseSchema provider-neutral JSON schema (lower-case types, {@code nullable} for optional values)
 */
public record LlmRequest(String correlationId, String systemPrompt, String userPrompt, JsonNode responseSchema) {
}
