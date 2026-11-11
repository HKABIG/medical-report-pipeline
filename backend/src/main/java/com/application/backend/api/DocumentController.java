package com.application.backend.api;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.application.backend.review.FieldReview;
import com.application.backend.review.ReviewRequest;
import com.application.backend.review.ReviewService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;
    private final ReviewService reviews;

    public DocumentController(DocumentService documents, ReviewService reviews) {
        this.documents = documents;
        this.reviews = reviews;
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return documents.list();
    }

    @GetMapping("/{id}")
    public DocumentDetail get(@PathVariable String id) {
        return documents.get(id);
    }

    /** Returns the stored decision, or 204 when the decision was cleared. */
    @PatchMapping("/{id}/cases/{caseIndex}/fields/{fieldId}")
    public ResponseEntity<FieldReview> review(@PathVariable String id, @PathVariable int caseIndex,
            @PathVariable String fieldId, @RequestBody ReviewRequest request) {
        Optional<FieldReview> review = reviews.apply(id, caseIndex, fieldId, request);
        return review.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
