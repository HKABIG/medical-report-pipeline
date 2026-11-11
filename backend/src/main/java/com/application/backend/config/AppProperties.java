package com.application.backend.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Path root,
        Path catalog,
        List<Path> documentDirs,
        Path outDir,
        Path fixturesDir,
        Path reviewsDir,
        Llm llm,
        Pipeline pipeline,
        Duplicates duplicates) {

    public Path catalogPath() {
        return resolve(catalog);
    }

    public List<Path> documentPaths() {
        return documentDirs.stream().map(this::resolve).toList();
    }

    public Path outPath() {
        return resolve(outDir);
    }

    public Path fixturesPath() {
        return resolve(fixturesDir);
    }

    public Path reviewsPath() {
        return resolve(reviewsDir);
    }

    private Path resolve(Path path) {
        return root.resolve(path).normalize();
    }

    public record Llm(
            String provider,
            Map<String, Provider> providers,
            int requestsPerMinute,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            Duration timeout) {

        public Provider activeProvider() {
            Provider active = providers.get(provider);
            if (active == null) {
                throw new IllegalStateException(
                        "app.llm.provider '" + provider + "' has no entry under app.llm.providers " + providers.keySet());
            }
            return active;
        }
    }

    /**
     * @param temperature   sampling temperature, or null to use the model's default
     * @param thinkingLevel reasoning effort for providers that support it, or null to use the model's default
     */
    public record Provider(String baseUrl, String model, String apiKeyEnv, Double temperature, String thinkingLevel) {
    }

    public record Pipeline(
            PipelineCommand command,
            boolean skipRecorded,
            int maxDocumentChars,
            Set<String> recommendedFlags,
            Set<String> exclusiveOptionCodes) {
    }

    public record Duplicates(
            int minComparableFields,
            double minAgreement,
            double highConfidenceAgreement,
            Set<String> ignoredSections) {
    }

    public enum PipelineCommand {
        EXTRACT, REPLAY
    }
}
