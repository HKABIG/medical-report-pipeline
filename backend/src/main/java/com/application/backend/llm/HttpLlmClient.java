package com.application.backend.llm;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Calls a provider over HTTP. Requests are spaced by the rate limiter; 429 and 5xx answers are retried with the
 * delay the provider asks for, else exponential backoff; an exhausted daily quota stops the run at once.
 * Every attempt is recorded, with the key removed, whether it succeeded or not.
 */
public class HttpLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(HttpLlmClient.class);
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);
    private static final String REDACTED = "[REDACTED]";

    private final LlmProvider provider;
    private final RestClient restClient;
    private final String apiKey;
    private final RateLimiter rateLimiter;
    private final Sleeper sleeper;
    private final FixtureStore fixtures;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public HttpLlmClient(LlmProvider provider, RestClient restClient, String apiKey, RateLimiter rateLimiter,
            Sleeper sleeper, FixtureStore fixtures, ObjectMapper objectMapper, int maxAttempts,
            Duration initialBackoff, Duration maxBackoff) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("No API key for " + provider.name());
        }
        this.provider = provider;
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.rateLimiter = rateLimiter;
        this.sleeper = sleeper;
        this.fixtures = fixtures;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        ProviderCall call = provider.prepare(request);
        String body = write(call.body());
        List<Fixture.Exchange> exchanges = new ArrayList<>();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            rateLimiter.acquire();
            log.info("{}: attempt {} of {} to {}", request.correlationId(), attempt, maxAttempts,
                    provider.qualifiedModel());
            long started = System.nanoTime();
            ResponseEntity<String> response;
            try {
                response = restClient.post()
                        .uri(call.uri())
                        .headers(headers -> provider.authHeaders(apiKey).forEach(headers::set))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(status -> true, (req, res) -> { })
                        .toEntity(String.class);
            } catch (ResourceAccessException e) {
                exchanges.add(exchange(attempt, call, null, null, "network: " + e.getMessage()));
                save(request, exchanges, null);
                log.warn("{}: network error after {}s: {}", request.correlationId(), seconds(started), e.getMessage());
                sleeper.sleep(backoff(attempt));
                continue;
            }
            int status = response.getStatusCode().value();
            log.info("{}: HTTP {} after {}s", request.correlationId(), status, seconds(started));
            JsonNode responseBody = readLenient(response.getBody());
            if (response.getStatusCode().is2xxSuccessful()) {
                try {
                    String text = provider.extractText(responseBody);
                    exchanges.add(exchange(attempt, call, status, responseBody, null));
                    save(request, exchanges, text);
                    return new LlmResponse(text, provider.qualifiedModel());
                } catch (LlmException e) {
                    exchanges.add(exchange(attempt, call, status, responseBody, e.getMessage()));
                    save(request, exchanges, null);
                    log.warn("{}: unusable answer: {}", request.correlationId(), e.getMessage());
                    sleeper.sleep(backoff(attempt));
                    continue;
                }
            }
            exchanges.add(exchange(attempt, call, status, responseBody, null));
            if (!RETRYABLE.contains(status)) {
                save(request, exchanges, null);
                throw new ProviderRejectedException(provider.name() + " answered HTTP " + status + ": "
                        + scrub(response.getBody()));
            }
            if (provider.isDailyQuotaExhausted(responseBody)) {
                save(request, exchanges, null);
                throw new QuotaExhaustedException(provider.name() + " daily quota exhausted; run again later, "
                        + "documents already recorded are skipped");
            }
            save(request, exchanges, null);
            Duration delay = retryDelay(attempt, response.getHeaders(), responseBody);
            log.warn("{}: HTTP {}, retrying in {}s", request.correlationId(), status, delay.toSeconds());
            sleeper.sleep(delay);
        }
        save(request, exchanges, null);
        throw new LlmException(provider.name() + " gave no usable answer for " + request.correlationId()
                + " after " + maxAttempts + " attempts");
    }

    private static String seconds(long startedNanos) {
        return String.format("%.1f", (System.nanoTime() - startedNanos) / 1e9);
    }

    private Duration retryDelay(int attempt, HttpHeaders headers, JsonNode body) {
        Optional<Duration> asked = provider.retryDelay(body).or(() -> retryAfter(headers));
        return asked.map(delay -> delay.plusSeconds(1)).filter(delay -> delay.compareTo(maxBackoff) <= 0)
                .orElseGet(() -> backoff(attempt));
    }

    private static Optional<Duration> retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(value.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Duration backoff(int attempt) {
        Duration delay = initialBackoff.multipliedBy(1L << Math.min(attempt - 1, 16));
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private Fixture.Exchange exchange(int attempt, ProviderCall call, Integer status, JsonNode response, String error) {
        return new Fixture.Exchange(attempt, call.uri().toString(), sanitize(call.body()), status,
                response == null ? null : sanitize(response), error == null ? null : scrub(error));
    }

    private void save(LlmRequest request, List<Fixture.Exchange> exchanges, String text) {
        fixtures.save(new Fixture(request.correlationId(), provider.name(), provider.model(), List.copyOf(exchanges),
                text == null ? null : scrub(text)));
    }

    private JsonNode sanitize(JsonNode node) {
        String text = write(node);
        return text.contains(apiKey) ? readLenient(scrub(text)) : node;
    }

    private String scrub(String text) {
        return text == null ? null : text.replace(apiKey, REDACTED);
    }

    private JsonNode readLenient(String text) {
        if (text == null || text.isBlank()) {
            return TextNode.valueOf("");
        }
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            return TextNode.valueOf(text);
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new LlmException("Cannot serialise request", e);
        }
    }
}
