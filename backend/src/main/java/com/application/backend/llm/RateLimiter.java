package com.application.backend.llm;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Spaces requests evenly so a run stays under the provider's requests-per-minute limit. */
public class RateLimiter {

    private final Duration interval;
    private final Clock clock;
    private final Sleeper sleeper;
    private Instant nextAllowed;

    public RateLimiter(int requestsPerMinute, Clock clock, Sleeper sleeper) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requests per minute must be positive");
        }
        this.interval = Duration.ofMinutes(1).dividedBy(requestsPerMinute);
        this.clock = clock;
        this.sleeper = sleeper;
        this.nextAllowed = Instant.MIN;
    }

    public synchronized void acquire() {
        Instant now = clock.instant();
        if (now.isBefore(nextAllowed)) {
            sleeper.sleep(Duration.between(now, nextAllowed));
            now = nextAllowed;
        }
        nextAllowed = now.plus(interval);
    }
}
