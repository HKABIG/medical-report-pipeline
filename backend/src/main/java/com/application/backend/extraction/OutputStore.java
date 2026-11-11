package com.application.backend.extraction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.application.backend.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** The out/ folder: one &lt;DOCUMENT_ID&gt;.json per document, plus duplicates.json. */
@Repository
public class OutputStore {

    public static final String DUPLICATES_FILE = "duplicates.json";
    private static final String SUFFIX = ".json";

    private final Path folder;
    private final ObjectMapper writer;
    private final ObjectMapper reader;

    @Autowired
    public OutputStore(AppProperties properties, ObjectMapper objectMapper) {
        this(properties.outPath(), objectMapper);
    }

    public OutputStore(Path folder, ObjectMapper objectMapper) {
        this.folder = folder;
        this.reader = objectMapper;
        this.writer = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path folder() {
        return folder;
    }

    public void writeDocument(String documentId, JsonNode output) {
        write(folder.resolve(documentId + SUFFIX), output);
    }

    public void writeDuplicates(JsonNode duplicates) {
        write(folder.resolve(DUPLICATES_FILE), duplicates);
    }

    public Optional<JsonNode> readDocument(String documentId) {
        return read(folder.resolve(documentId + SUFFIX));
    }

    public Optional<JsonNode> readDuplicates() {
        return read(folder.resolve(DUPLICATES_FILE));
    }

    public List<String> documentIds() {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(SUFFIX) && !name.equals(DUPLICATES_FILE))
                    .map(name -> name.substring(0, name.length() - SUFFIX.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + folder, e);
        }
    }

    private Optional<JsonNode> read(Path path) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(reader.readTree(path.toFile()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    private void write(Path path, JsonNode node) {
        try {
            Files.createDirectories(path.getParent());
            writer.writeValue(path.toFile(), node);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + path, e);
        }
    }
}
