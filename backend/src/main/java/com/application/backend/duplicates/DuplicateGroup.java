package com.application.backend.duplicates;

import java.util.List;

import com.application.backend.extraction.Confidence;

public record DuplicateGroup(List<String> documentIds, Confidence confidence, String reason) {
}
