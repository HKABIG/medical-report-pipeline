package com.application.backend.llm;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.application.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Creates the client that calls the configured provider. Only the extract command asks for it, so the API and
 * replay never need a key; asking without a key fails with a message that names the variable to set.
 */
public class LiveLlmClientFactory {

    private final LlmProvider provider;
    private final AppProperties.Llm settings;
    private final FixtureStore fixtures;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private HttpLlmClient client;

    public LiveLlmClientFactory(LlmProvider provider, AppProperties.Llm settings, FixtureStore fixtures,
            ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.provider = provider;
        this.settings = settings;
        this.fixtures = fixtures;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    public synchronized LlmClient get() {
        if (client == null) {
            String keyVariable = settings.activeProvider().apiKeyEnv();
            String apiKey = System.getenv(keyVariable);
            if (apiKey == null || apiKey.isBlank()) {
                throw new LlmException("No API key for " + provider.name() + ": set the environment variable "
                        + keyVariable + ", or use replay, which needs none");
            }
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(settings.timeout()).build());
            requestFactory.setReadTimeout(settings.timeout());
            client = new HttpLlmClient(provider, restClientBuilder.requestFactory(requestFactory).build(), apiKey,
                    new RateLimiter(settings.requestsPerMinute(), Clock.systemUTC(), Sleeper.REAL), Sleeper.REAL,
                    fixtures, objectMapper, settings.maxAttempts(), settings.initialBackoff(), settings.maxBackoff());
        }
        return client;
    }
}
