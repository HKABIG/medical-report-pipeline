package com.application.backend.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.application.backend.document.DocumentFormat;
import com.application.backend.document.DocumentRepository;
import com.application.backend.document.SourceDocument;
import com.application.backend.document.SourceText;
import com.application.backend.extraction.OutputKeys;
import com.application.backend.extraction.OutputStore;
import com.application.backend.review.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;

/** Joins the committed outputs with their source documents and review state. */
@Service
public class DocumentService {

    private final OutputStore outputs;
    private final DocumentRepository documents;
    private final ReviewService reviews;

    public DocumentService(OutputStore outputs, DocumentRepository documents, ReviewService reviews) {
        this.outputs = outputs;
        this.documents = documents;
        this.reviews = reviews;
    }

    public List<DocumentSummary> list() {
        List<DocumentSummary> summaries = new ArrayList<>();
        for (String id : outputs.documentIds()) {
            JsonNode output = outputs.readDocument(id).orElseThrow();
            Optional<SourceDocument> source = documents.find(id);
            summaries.add(new DocumentSummary(id, source.map(SourceDocument::title).orElse(id), format(source),
                    output.path(OutputKeys.CASES).size(), flags(output), reviews.progress(id, output)));
        }
        return summaries;
    }

    public DocumentDetail get(String id) {
        JsonNode output = outputs.readDocument(id).orElseThrow(() -> new NotFoundException("No document " + id));
        Optional<SourceDocument> source = documents.find(id);
        String text = source.flatMap(SourceDocument::text).map(SourceText::text).orElse(null);
        return new DocumentDetail(id, source.map(SourceDocument::title).orElse(id), format(source), text, output,
                reviews.review(id), reviews.progress(id, output));
    }

    private static DocumentFormat format(Optional<SourceDocument> source) {
        return source.map(SourceDocument::format).orElse(null);
    }

    private static List<String> flags(JsonNode output) {
        List<String> flags = new ArrayList<>();
        output.path(OutputKeys.FLAGS).forEach(flag -> flags.add(flag.asText()));
        return flags;
    }
}
