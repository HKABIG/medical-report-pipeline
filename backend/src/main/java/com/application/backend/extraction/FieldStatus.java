package com.application.backend.extraction;

import java.util.Arrays;
import java.util.Optional;

/** The statuses every field result carries, as defined by the output schema. */
public enum FieldStatus {
    EXTRACTED("extracted"),
    UNKNOWN("unknown"),
    CONFLICT("conflict");

    private final String code;

    FieldStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<FieldStatus> fromCode(String code) {
        return Arrays.stream(values()).filter(status -> status.code.equals(code)).findFirst();
    }
}
