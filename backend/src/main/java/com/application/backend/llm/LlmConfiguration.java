package com.application.backend.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.application.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Wires the configured provider. Switching provider is a change of app.llm.provider, nothing else. */
@Configuration
public class LlmConfiguration {

    @Bean
    FixtureStore fixtureStore(AppProperties properties, ObjectMapper objectMapper) {
        return new FixtureStore(properties.fixturesPath(), objectMapper);
    }

    @Bean
    LlmProvider llmProvider(AppProperties properties) {
        AppProperties.Llm llm = properties.llm();
        AppProperties.Provider active = llm.activeProvider();
        return switch (llm.provider()) {
            case GeminiProvider.NAME -> new GeminiProvider(active.baseUrl(), active.model(), active.temperature(),
                    active.thinkingLevel());
            case OpenAiCompatibleProvider.NAME ->
                new OpenAiCompatibleProvider(active.baseUrl(), active.model(), active.temperature());
            default -> throw new IllegalStateException("No provider implementation named '" + llm.provider() + "'");
        };
    }

    @Bean
    LiveLlmClientFactory liveLlmClientFactory(LlmProvider provider, AppProperties properties, FixtureStore fixtures,
            ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        return new LiveLlmClientFactory(provider, properties.llm(), fixtures, objectMapper, restClientBuilder);
    }

    @Bean
    ReplayLlmClient replayLlmClient(FixtureStore fixtures) {
        return new ReplayLlmClient(fixtures);
    }
}
