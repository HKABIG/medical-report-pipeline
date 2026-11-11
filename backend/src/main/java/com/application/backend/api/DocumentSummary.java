package com.application.backend.api;

import java.util.List;

import com.application.backend.document.DocumentFormat;
import com.application.backend.review.ReviewProgress;

public record DocumentSummary(String id, String title, DocumentFormat format, int caseCount, List<String> flags,
        ReviewProgress progress) {
}
