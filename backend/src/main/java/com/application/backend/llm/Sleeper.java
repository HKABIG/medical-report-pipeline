package com.application.backend.llm;

import java.time.Duration;

/** Waiting, behind an interface so tests do not wait. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration);

    Sleeper REAL = duration -> {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting", e);
        }
    };
}
