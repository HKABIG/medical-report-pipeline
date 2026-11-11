package com.application.backend.api;

import com.application.backend.document.DocumentFormat;
import com.application.backend.review.DocumentReview;
import com.application.backend.review.ReviewProgress;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * @param text       the document text, or null when the format has none
 * @param extraction the output file exactly as written by the pipeline
 */
public record DocumentDetail(String id, String title, DocumentFormat format, String text, JsonNode extraction,
        DocumentReview review, ReviewProgress progress) {
}
