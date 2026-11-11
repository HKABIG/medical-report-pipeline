package com.application.backend.extraction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.application.backend.document.SourceText;

/**
 * Finds a model's quote in the document. Matching ignores case and runs of whitespace (the same tolerance the
 * reviewer's checker applies); the span returned is always the document's own text, so it never paraphrases.
 * A quote the model stitched together with an ellipsis is located piece by piece.
 */
@Component
public class QuoteLocator {

    /** Shorter quotes cannot be checked meaningfully. */
    public static final int MIN_QUOTE_LENGTH = 3;

    private static final Pattern ELLIPSIS = Pattern.compile("\\.\\.\\.|\u2026");

    public List<Span> locate(SourceText text, String quote) {
        List<Span> spans = new ArrayList<>();
        for (String piece : ELLIPSIS.split(quote)) {
            find(text, piece).ifPresent(spans::add);
        }
        return spans;
    }

    public Optional<Span> find(SourceText text, String quote) {
        NormalizedText haystack = NormalizedText.of(text);
        String needle = NormalizedText.normalize(quote);
        if (needle.length() < MIN_QUOTE_LENGTH) {
            return Optional.empty();
        }
        int index = haystack.normalized().indexOf(needle);
        if (index < 0) {
            return Optional.empty();
        }
        int start = haystack.originalStart(index);
        int end = haystack.originalEnd(index + needle.length() - 1);
        OptionalInt page = text.pageAt(start);
        return Optional.of(new Span(text.substring(start, end), start, end, page));
    }

    /** Lower-cased text with whitespace runs collapsed, and a map from each normalised char back to a code point. */
    record NormalizedText(String normalized, int[] origin) {

        static NormalizedText of(SourceText text) {
            StringBuilder out = new StringBuilder();
            int[] origin = new int[text.length()];
            int count = 0;
            boolean pendingSpace = false;
            for (int i = 0; i < text.length(); i++) {
                int cp = text.codePointAt(i);
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                    pendingSpace = count > 0;
                    continue;
                }
                if (pendingSpace) {
                    out.append(' ');
                    origin[count++] = i - 1;
                    pendingSpace = false;
                }
                out.append(fold(cp));
                origin[count++] = i;
            }
            int[] trimmed = new int[count];
            System.arraycopy(origin, 0, trimmed, 0, count);
            return new NormalizedText(out.toString(), trimmed);
        }

        static String normalize(String quote) {
            StringBuilder out = new StringBuilder();
            boolean pendingSpace = false;
            for (int cp : quote.strip().codePoints().toArray()) {
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                    pendingSpace = !out.isEmpty();
                    continue;
                }
                if (pendingSpace) {
                    out.append(' ');
                    pendingSpace = false;
                }
                out.append(fold(cp));
            }
            return out.toString();
        }

        int originalStart(int normalizedIndex) {
            return origin[normalizedIndex];
        }

        int originalEnd(int normalizedLastIndex) {
            return origin[normalizedLastIndex] + 1;
        }

        /** One char per code point keeps the index map simple; dashes and quotes fold as the checker folds them. */
        private static char fold(int cp) {
            return switch (cp) {
                case '\u2013', '\u2014' -> '-';
                case '\u2019' -> '\'';
                default -> {
                    int lower = Character.toLowerCase(cp);
                    yield Character.isBmpCodePoint(lower) ? (char) lower : '\uFFFD';
                }
            };
        }
    }
}
