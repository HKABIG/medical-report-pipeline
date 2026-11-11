package com.application.backend.llm;

import java.net.URI;

import com.fasterxml.jackson.databind.JsonNode;

/** What to send for one request, without credentials. */
public record ProviderCall(URI uri, JsonNode body) {
}
