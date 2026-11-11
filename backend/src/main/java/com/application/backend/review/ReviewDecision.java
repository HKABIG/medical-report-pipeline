package com.application.backend.review;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** A reviewer's decision on one field. PENDING clears an earlier decision. */
public enum ReviewDecision {
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    EDITED("edited"),
    PENDING("pending");

    private final String code;

    ReviewDecision(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ReviewDecision fromCode(String code) {
        return Arrays.stream(values()).filter(decision -> decision.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown decision '" + code + "'"));
    }
}
