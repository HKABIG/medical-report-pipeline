package com.application.backend.document;

import java.util.Optional;

/** One input document. {@code text} is empty when the format carries no readable text. */
public record SourceDocument(String id, DocumentFormat format, Optional<SourceText> text) {

    public static SourceDocument ofText(String id, String text) {
        return new SourceDocument(id, DocumentFormat.TEXT, Optional.of(new SourceText(text)));
    }

    public String title() {
        return text.flatMap(SourceText::title).orElse(id);
    }
}
