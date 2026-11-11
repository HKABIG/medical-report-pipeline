package com.application.backend.review;

import com.fasterxml.jackson.databind.JsonNode;

/** Body of PATCH /api/documents/{id}/cases/{caseIndex}/fields/{fieldId}. */
public record ReviewRequest(ReviewDecision decision, JsonNode value) {
}
