package com.application.backend.extraction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.application.backend.catalog.CatalogValidator;
import com.application.backend.catalog.FieldDef;
import com.application.backend.document.SourceText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;

import static com.application.backend.extraction.AnswerKeys.*;

/**
 * Turns the model's answer for one field into a field result the reviewer can trust. A value is kept only when it
 * passes the catalog and at least one of its quotes is found in the document; otherwise the field is unknown with a
 * note saying why. Works for any catalog field, including fields the recording does not cover.
 */
@Component
public class FieldResolver {

    public static final String NOT_COVERED = "Not covered by the model answer; the field may be newer than the recording.";

    private final CatalogValidator validator;
    private final QuoteLocator locator;

    public FieldResolver(CatalogValidator validator, QuoteLocator locator) {
        this.validator = validator;
        this.locator = locator;
    }

    public FieldResult resolve(FieldDef field, JsonNode answer, SourceText text) {
        if (answer == null || !answer.isObject()) {
            return FieldResult.unknown(NOT_COVERED);
        }
        String modelNote = textOrNull(answer.get(NOTE));
        Optional<FieldStatus> status = FieldStatus.fromCode(answer.path(STATUS).asText());
        if (status.isEmpty()) {
            return FieldResult.unknown("The model gave no valid status (" + answer.path(STATUS) + ").");
        }
        return switch (status.get()) {
            case UNKNOWN -> FieldResult.unknown(modelNote);
            case CONFLICT -> FieldResult.conflict(locateAll(text, quotes(answer)).found(),
                    modelNote != null ? modelNote : "The document states incompatible values.");
            case EXTRACTED -> resolveExtracted(field, answer, text, modelNote);
        };
    }

    private FieldResult resolveExtracted(FieldDef field, JsonNode answer, SourceText text, String modelNote) {
        JsonNode raw = answer.get(VALUE);
        Optional<JsonNode> value = coerce(field, raw);
        if (value.isEmpty()) {
            return FieldResult.unknown("Model value " + raw + " is not a " + field.type().code() + "; not used.");
        }
        Optional<String> invalid = validator.validate(field, value.get());
        if (invalid.isPresent()) {
            return FieldResult.unknown("Model value " + value.get() + " fails the catalog (" + invalid.get()
                    + "); not used.");
        }
        Located located = locateAll(text, quotes(answer));
        if (located.found().isEmpty()) {
            return FieldResult.unknown("Model value " + value.get()
                    + " dropped: none of its quotes occur in the document " + located.missing() + ".");
        }
        List<String> notes = new ArrayList<>();
        if (modelNote != null) {
            notes.add(modelNote);
        }
        Confidence confidence = Confidence.fromCode(answer.path(CONFIDENCE).asText()).orElseGet(() -> {
            notes.add("The model gave no confidence; set to low.");
            return Confidence.LOW;
        });
        if (answer.path(DERIVED).asBoolean(false) && confidence == Confidence.HIGH) {
            confidence = Confidence.MEDIUM;
            notes.add("Computed rather than stated; confidence capped at medium.");
        }
        if (!located.missing().isEmpty()) {
            notes.add("Quotes not found in the document and omitted: " + located.missing() + ".");
        }
        return FieldResult.extracted(value.get(), confidence, located.found(),
                notes.isEmpty() ? null : String.join(" ", notes));
    }

    private Located locateAll(SourceText text, List<String> quotes) {
        Map<String, Span> found = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String quote : quotes) {
            List<Span> spans = locator.locate(text, quote);
            if (spans.isEmpty()) {
                missing.add(quote);
            }
            spans.forEach(span -> found.putIfAbsent(span.start() + ":" + span.end(), span));
        }
        return new Located(List.copyOf(found.values()), List.copyOf(missing));
    }

    /** Brings a model value into the catalog type without changing its meaning; empty when that is not possible. */
    static Optional<JsonNode> coerce(FieldDef field, JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return Optional.empty();
        }
        return switch (field.type()) {
            case STRING, DATE, CODED -> raw.isTextual() ? Optional.of(TextNode.valueOf(raw.asText().strip()))
                    : Optional.empty();
            case INTEGER, NUMBER -> number(raw);
            case CODED_MULTI, STRING_LIST -> list(raw);
        };
    }

    private static Optional<JsonNode> number(JsonNode raw) {
        BigDecimal number;
        if (raw.isNumber()) {
            number = raw.decimalValue();
        } else if (raw.isTextual()) {
            try {
                number = new BigDecimal(raw.asText().strip());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
        BigDecimal stripped = number.stripTrailingZeros();
        return Optional.of(stripped.scale() <= 0 ? LongNode.valueOf(stripped.longValueExact())
                : DoubleNode.valueOf(stripped.doubleValue()));
    }

    private static Optional<JsonNode> list(JsonNode raw) {
        if (!raw.isArray()) {
            return Optional.empty();
        }
        Set<String> seen = new LinkedHashSet<>();
        ArrayNode out = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : raw) {
            if (!item.isTextual()) {
                return Optional.empty();
            }
            String text = item.asText().strip();
            if (!text.isEmpty() && seen.add(text.toLowerCase(Locale.ROOT))) {
                out.add(text);
            }
        }
        return Optional.of(out);
    }

    private static List<String> quotes(JsonNode answer) {
        List<String> quotes = new ArrayList<>();
        answer.path(QUOTES).forEach(quote -> {
            if (quote.isTextual() && !quote.asText().isBlank()) {
                quotes.add(quote.asText());
            }
        });
        return quotes;
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() || node.asText().isBlank() ? null : node.asText().strip();
    }

    private record Located(List<Span> found, List<String> missing) {
    }
}
