package com.application.backend.extraction;

import java.util.OptionalInt;

/** A located quote: the exact document text between code-point offsets [start, end). */
public record Span(String quote, int start, int end, OptionalInt page) {
}
