package com.application.backend.cli;

import java.util.List;

import com.application.backend.duplicates.DuplicateGroup;
import com.application.backend.extraction.ExtractionResult;

/** What a run produced. Outputs are written only when {@code failures} is empty. */
public record PipelineReport(List<ExtractionResult> results, List<DuplicateGroup> duplicates, List<String> failures) {

    public boolean succeeded() {
        return failures.isEmpty();
    }
}
