package com.application.backend.extraction;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One patient's case. Fields are keyed by catalog field id, in catalog order. */
public record CaseResult(int caseIndex, String label, Map<String, FieldResult> fields) {

    public CaseResult {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
