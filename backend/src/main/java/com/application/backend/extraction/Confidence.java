package com.application.backend.extraction;

import java.util.Arrays;
import java.util.Optional;

/** Confidence levels, lowest first. */
public enum Confidence {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    private final String code;

    Confidence(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public Confidence atMost(Confidence cap) {
        return compareTo(cap) > 0 ? cap : this;
    }

    public static Optional<Confidence> fromCode(String code) {
        return Arrays.stream(values()).filter(level -> level.code.equals(code)).findFirst();
    }
}
