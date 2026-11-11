package com.application.backend.review;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.application.backend.api.BadRequestException;
import com.application.backend.api.NotFoundException;
import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.CatalogService;
import com.application.backend.catalog.CatalogValidator;
import com.application.backend.catalog.FieldDef;
import com.application.backend.extraction.OutputKeys;
import com.application.backend.extraction.OutputStore;
import com.fasterxml.jackson.databind.JsonNode;

/** Applies review decisions. An edited value must pass the catalog like any model value. */
@Service
public class ReviewService {

    private final ReviewStore store;
    private final OutputStore outputs;
    private final CatalogService catalogs;
    private final CatalogValidator validator;
    private final Clock clock;

    public ReviewService(ReviewStore store, OutputStore outputs, CatalogService catalogs, CatalogValidator validator,
            Clock clock) {
        this.store = store;
        this.outputs = outputs;
        this.catalogs = catalogs;
        this.validator = validator;
        this.clock = clock;
    }

    public DocumentReview review(String documentId) {
        return store.load(documentId);
    }

    public ReviewProgress progress(String documentId, JsonNode output) {
        int total = output.path(OutputKeys.CASES).size() * catalogs.current().fields().size();
        return new ReviewProgress(Math.min(store.load(documentId).decided(), total), total);
    }

    public synchronized Optional<FieldReview> apply(String documentId, int caseIndex, String fieldId,
            ReviewRequest request) {
        JsonNode output = outputs.readDocument(documentId)
                .orElseThrow(() -> new NotFoundException("No document " + documentId));
        if (caseIndex < 0 || caseIndex >= output.path(OutputKeys.CASES).size()) {
            throw new NotFoundException("Document " + documentId + " has no case " + caseIndex);
        }
        Catalog catalog = catalogs.current();
        FieldDef field = catalog.field(fieldId)
                .orElseThrow(() -> new NotFoundException("The catalog has no field " + fieldId));
        if (request == null || request.decision() == null) {
            throw new BadRequestException("A decision is required");
        }
        if (request.decision() == ReviewDecision.EDITED) {
            validator.validate(field, request.value()).ifPresent(reason -> {
                throw new BadRequestException("Value rejected by the catalog: " + reason);
            });
        } else if (request.value() != null && !request.value().isNull()) {
            throw new BadRequestException("A value is accepted only with the decision 'edited'");
        }

        DocumentReview review = store.load(documentId);
        Map<Integer, Map<String, FieldReview>> cases = new TreeMap<>(review.cases());
        Map<String, FieldReview> fields = new TreeMap<>(cases.getOrDefault(caseIndex, Map.of()));
        Optional<FieldReview> result;
        if (request.decision() == ReviewDecision.PENDING) {
            fields.remove(fieldId);
            result = Optional.empty();
        } else {
            FieldReview fieldReview = new FieldReview(request.decision(),
                    request.decision() == ReviewDecision.EDITED ? request.value() : null, clock.instant());
            fields.put(fieldId, fieldReview);
            result = Optional.of(fieldReview);
        }
        if (fields.isEmpty()) {
            cases.remove(caseIndex);
        } else {
            cases.put(caseIndex, fields);
        }
        store.save(documentId, new DocumentReview(cases));
        return result;
    }
}
