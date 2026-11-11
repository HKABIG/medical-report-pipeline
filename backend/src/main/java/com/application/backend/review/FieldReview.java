package com.application.backend.review;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** @param value the reviewer's value, present only for EDITED */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldReview(ReviewDecision decision, JsonNode value, Instant reviewedAt) {
}
