package com.application.backend.review;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.application.backend.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** reviews/&lt;DOCUMENT_ID&gt;.json. Review state is kept apart from out/, which stays exactly what the model produced. */
@Repository
public class ReviewStore {

    private static final String SUFFIX = ".json";

    private final Path folder;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReviewStore(AppProperties properties, ObjectMapper objectMapper) {
        this(properties.reviewsPath(), objectMapper);
    }

    public ReviewStore(Path folder, ObjectMapper objectMapper) {
        this.folder = folder;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public synchronized DocumentReview load(String documentId) {
        Path path = folder.resolve(documentId + SUFFIX);
        if (!Files.exists(path)) {
            return DocumentReview.empty();
        }
        try {
            return objectMapper.readValue(path.toFile(), DocumentReview.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }

    public synchronized void save(String documentId, DocumentReview review) {
        try {
            Files.createDirectories(folder);
            objectMapper.writeValue(folder.resolve(documentId + SUFFIX).toFile(), review);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write review for " + documentId, e);
        }
    }
}
