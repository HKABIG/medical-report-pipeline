package com.application.backend.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** fixtures/&lt;correlation id&gt;.json, one file per document. */
public class FixtureStore {

    private static final String SUFFIX = ".json";

    private final Path folder;
    private final ObjectMapper objectMapper;

    public FixtureStore(Path folder, ObjectMapper objectMapper) {
        this.folder = folder;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Optional<Fixture> load(String correlationId) {
        Path path = pathOf(correlationId);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), Fixture.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read fixture " + path, e);
        }
    }

    public void save(Fixture fixture) {
        try {
            Files.createDirectories(folder);
            objectMapper.writeValue(pathOf(fixture.correlationId()).toFile(), fixture);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write fixture for " + fixture.correlationId(), e);
        }
    }

    public boolean hasAnswer(String correlationId) {
        return load(correlationId).flatMap(Fixture::answer).isPresent();
    }

    private Path pathOf(String correlationId) {
        return folder.resolve(correlationId + SUFFIX);
    }
}
