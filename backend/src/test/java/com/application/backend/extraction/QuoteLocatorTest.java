package com.application.backend.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import com.application.backend.document.SourceText;

class QuoteLocatorTest {

    private final QuoteLocator locator = new QuoteLocator();

    @Test
    void findsExactQuote() {
        SourceText text = new SourceText("Patient: Mrs M.K., a 54-year-old woman.");
        Span span = locator.find(text, "54-year-old woman").orElseThrow();
        assertThat(span.quote()).isEqualTo("54-year-old woman");
        assertThat(text.substring(span.start(), span.end())).isEqualTo(span.quote());
    }

    @Test
    void ignoresCaseAndWhitespaceRunsAndReturnsTheDocumentsOwnText() {
        SourceText text = new SourceText("Atorvastatin 40 mg orally\n   once daily");
        Span span = locator.find(text, "atorvastatin 40 mg ORALLY once daily").orElseThrow();
        assertThat(span.quote()).isEqualTo("Atorvastatin 40 mg orally\n   once daily");
        assertThat(span.start()).isZero();
        assertThat(span.end()).isEqualTo(text.length());
    }

    @Test
    void countsOffsetsInCodePointsNotUtf16Units() {
        String emoji = new String(Character.toChars(0x1F600));
        SourceText text = new SourceText(emoji + " fever 38.6");
        Span span = locator.find(text, "fever").orElseThrow();
        assertThat(span.start()).isEqualTo(2);
        assertThat(span.end()).isEqualTo(7);
    }

    @Test
    void foldsTypographicDashesLikeTheChecker() {
        SourceText text = new SourceText("piperacillin\u2013tazobactam started");
        assertThat(locator.find(text, "piperacillin-tazobactam")).isPresent();
    }

    @Test
    void rejectsQuotesNotInTheDocumentOrTooShort() {
        SourceText text = new SourceText("She recovered fully.");
        assertThat(locator.find(text, "she died")).isEmpty();
        assertThat(locator.find(text, "sh")).isEmpty();
    }

    @Test
    void splitsEllipsisQuotesIntoSeparateSpans() {
        SourceText text = new SourceText("Ibuprofen 400 mg tablets, one tablet by mouth three times a day");
        List<Span> spans = locator.locate(text, "Ibuprofen 400 mg ... by mouth");
        assertThat(spans).extracting(Span::quote).containsExactly("Ibuprofen 400 mg", "by mouth");
    }

    @Test
    void reportsThePageFromPageMarkers() {
        SourceText text = new SourceText("Header\n  Page 1 of 2\nalpha\n  Page 2 of 2\nbeta gamma");
        assertThat(locator.find(text, "beta gamma").orElseThrow().page()).isEqualTo(OptionalInt.of(2));
        assertThat(locator.find(text, "Header").orElseThrow().page()).isEqualTo(OptionalInt.of(1));
        assertThat(locator.find(new SourceText("no markers here"), "markers").orElseThrow().page()).isEmpty();
    }
}
