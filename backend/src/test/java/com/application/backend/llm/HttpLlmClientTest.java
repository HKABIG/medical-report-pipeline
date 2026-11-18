package com.application.backend.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.application.backend.TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class HttpLlmClientTest {

    private static final String KEY = "test-key-0123456789";
    private static final String URL = "https://llm.example/v1beta/models/m:generateContent";
    private static final String ANSWER = "{\"cases\":[]}";
    private static final String OK_BODY = """
            {"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"%s"}]},
             "finishReason":"STOP"}]}""".formatted(ANSWER.replace("\"", "\\\""));
    private static final String MINUTE_LIMIT = """
            {"error":{"code":429,"details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"7s"}]}}""";
    private static final String DAY_LIMIT = """
            {"error":{"code":429,"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure",
             "violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier"}]}]}}""";

    @TempDir
    Path fixturesDir;

    private MockRestServiceServer server;
    private HttpLlmClient client;
    private FixtureStore fixtures;
    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        fixtures = new FixtureStore(fixturesDir, TestSupport.JSON);
        Sleeper sleeper = sleeps::add;
        client = new HttpLlmClient(new GeminiProvider("https://llm.example/v1beta", "m", null, null), builder.build(), KEY,
                new RateLimiter(600, Clock.systemUTC(), sleeper), sleeper, fixtures, TestSupport.JSON, 3,
                Duration.ofSeconds(2), Duration.ofSeconds(60));
    }

    private LlmRequest request() {
        return new LlmRequest("D01", "system", "user", JsonNodeFactory.instance.objectNode().put("type", "object"));
    }

    @Test
    void retriesAPerMinuteLimitWithTheDelayTheProviderAsksForAndRecordsBothAttempts() throws Exception {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", KEY))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body(MINUTE_LIMIT));
        server.expect(requestTo(URL)).andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        LlmResponse response = client.complete(request());

        server.verify();
        assertThat(response.text()).isEqualTo(ANSWER);
        assertThat(response.model()).isEqualTo("gemini/m");
        assertThat(sleeps).contains(Duration.ofSeconds(8));
        Fixture fixture = fixtures.load("D01").orElseThrow();
        assertThat(fixture.exchanges()).extracting(Fixture.Exchange::status).containsExactly(429, 200);
        assertThat(fixture.answer()).contains(ANSWER);
    }

    @Test
    void stopsAtOnceWhenTheDailyQuotaIsExhausted() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body(DAY_LIMIT));

        assertThatThrownBy(() -> client.complete(request())).isInstanceOf(QuotaExhaustedException.class);
        server.verify();
        assertThat(fixtures.hasAnswer("D01")).isFalse();
    }

    @Test
    void doesNotRetryARejectedRequest() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"error\":{\"message\":\"models/m is no longer available\"}}"));
        assertThatThrownBy(() -> client.complete(request())).isInstanceOf(ProviderRejectedException.class)
                .hasMessageContaining("404").hasMessageContaining("no longer available");
        server.verify();
    }

    @Test
    void leavesTheTemperatureToTheModelUnlessConfigured() {
        LlmRequest request = request();
        assertThat(new GeminiProvider("u", "m", null, null).prepare(request).body().path("generationConfig")
                .has("temperature")).isFalse();
        JsonNode configured = new GeminiProvider("u", "m", 0.0, "low").prepare(request).body().path("generationConfig");
        assertThat(configured.path("temperature").asDouble(-1)).isZero();
        assertThat(configured.path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("low");
    }

    @Test
    void neverWritesTheKeyToAFixtureEvenWhenTheProviderEchoesIt() throws Exception {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("{\"error\":{\"message\":\"API key " + KEY + " is invalid\"}}"));
        assertThatThrownBy(() -> client.complete(request())).isInstanceOf(LlmException.class)
                .hasMessageNotContaining(KEY);
        String recorded = Files.readString(fixturesDir.resolve("D01.json"));
        assertThat(recorded).doesNotContain(KEY).contains("[REDACTED]");
    }

    @Test
    void refusesToStartWithoutAKey() {
        assertThatThrownBy(() -> new HttpLlmClient(new GeminiProvider("u", "m", null, null), RestClient.create(), " ",
                new RateLimiter(1, Clock.systemUTC(), d -> { }), d -> { }, fixtures, TestSupport.JSON, 1,
                Duration.ZERO, Duration.ZERO)).isInstanceOf(LlmException.class).hasMessageContaining("API key");
    }

    @Test
    void replayAnswersFromTheRecordingOnly() {
        fixtures.save(new Fixture("D02", "gemini", "m", List.of(), ANSWER));
        ReplayLlmClient replay = new ReplayLlmClient(fixtures);
        assertThat(replay.complete(new LlmRequest("D02", "s", "u", null)).text()).isEqualTo(ANSWER);
        assertThatThrownBy(() -> replay.complete(new LlmRequest("D03", "s", "u", null)))
                .isInstanceOf(LlmException.class).hasMessageContaining("No recording");
    }
}
