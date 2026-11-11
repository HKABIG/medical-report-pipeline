package com.application.backend.extraction;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** One field of one case: status, and value, confidence and provenance when extracted. */
public record FieldResult(FieldStatus status, JsonNode value, Confidence confidence, List<Span> provenance, String note) {

    public FieldResult {
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }

    public static FieldResult unknown(String note) {
        return new FieldResult(FieldStatus.UNKNOWN, null, null, List.of(), note);
    }

    public static FieldResult conflict(List<Span> provenance, String note) {
        return new FieldResult(FieldStatus.CONFLICT, null, null, provenance, note);
    }

    public static FieldResult extracted(JsonNode value, Confidence confidence, List<Span> provenance, String note) {
        return new FieldResult(FieldStatus.EXTRACTED, value, confidence, provenance, note);
    }
}
