package com.application.backend.extraction;

import java.util.ArrayList;
import java.util.List;

/** The output for one document. {@code model} is null when no model was asked, e.g. for an unsupported format. */
public record ExtractionResult(String documentId, String catalogVersion, String model, List<String> flags,
        List<CaseResult> cases) {

    public ExtractionResult {
        flags = List.copyOf(flags);
        cases = List.copyOf(cases);
    }

    public ExtractionResult withFlag(String flag) {
        if (flags.contains(flag)) {
            return this;
        }
        List<String> more = new ArrayList<>(flags);
        more.add(flag);
        return new ExtractionResult(documentId, catalogVersion, model, more, cases);
    }
}
