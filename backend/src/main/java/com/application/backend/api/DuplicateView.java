package com.application.backend.api;

import java.util.List;

import com.application.backend.duplicates.CaseComparison;
import com.fasterxml.jackson.databind.JsonNode;

/** One duplicate group from out/duplicates.json with the field-by-field comparison of each pair of documents. */
public record DuplicateView(List<String> documentIds, String confidence, String reason,
        List<CaseComparison> comparisons) {

    public record Response(List<DuplicateView> groups) {
    }

    static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
