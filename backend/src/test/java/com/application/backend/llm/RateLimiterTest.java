package com.application.backend.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void spacesRequestsEvenlyAcrossAMinute() {
        List<Duration> sleeps = new ArrayList<>();
        Clock frozen = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(6, frozen, sleeps::add);
        limiter.acquire();
        limiter.acquire();
        assertThat(sleeps).containsExactly(Duration.ofSeconds(10));
    }
}
