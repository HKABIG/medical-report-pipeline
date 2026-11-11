package com.application.backend.duplicates;

/** @param counted false for fields in sections that describe the report, not the case */
public record FieldComparison(String fieldId, Outcome outcome, boolean counted) {
}
