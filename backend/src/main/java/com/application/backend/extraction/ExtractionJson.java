package com.application.backend.extraction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static com.application.backend.extraction.OutputKeys.*;

/** Maps an extraction result to and from the output file format. Key order is fixed so outputs diff cleanly. */
@Component
public class ExtractionJson {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    public ObjectNode write(ExtractionResult result) {
        ObjectNode root = JSON.objectNode();
        root.put(DOCUMENT_ID, result.documentId());
        root.put(CATALOG_VERSION, result.catalogVersion());
        if (result.model() != null) {
            root.put(MODEL, result.model());
        }
        ArrayNode flags = root.putArray(FLAGS);
        result.flags().forEach(flags::add);
        ArrayNode cases = root.putArray(CASES);
        for (CaseResult caseResult : result.cases()) {
            ObjectNode node = cases.addObject();
            node.put(CASE_INDEX, caseResult.caseIndex());
            node.put(LABEL, caseResult.label());
            ObjectNode fields = node.putObject(FIELDS);
            caseResult.fields().forEach((id, field) -> fields.set(id, writeField(field)));
        }
        return root;
    }

    private ObjectNode writeField(FieldResult field) {
        ObjectNode node = JSON.objectNode();
        node.put(STATUS, field.status().code());
        if (field.value() != null) {
            node.set(VALUE, field.value());
        }
        if (field.confidence() != null) {
            node.put(CONFIDENCE, field.confidence().code());
        }
        if (!field.provenance().isEmpty()) {
            ArrayNode provenance = node.putArray(PROVENANCE);
            for (Span span : field.provenance()) {
                ObjectNode item = provenance.addObject();
                item.put(QUOTE, span.quote());
                item.put(START, span.start());
                item.put(END, span.end());
                span.page().ifPresent(page -> item.put(PAGE, page));
            }
        }
        if (field.note() != null) {
            node.put(NOTE, field.note());
        }
        return node;
    }

    public ExtractionResult read(JsonNode root) {
        List<String> flags = new ArrayList<>();
        root.path(FLAGS).forEach(flag -> flags.add(flag.asText()));
        List<CaseResult> cases = new ArrayList<>();
        for (JsonNode node : root.path(CASES)) {
            Map<String, FieldResult> fields = new LinkedHashMap<>();
            node.path(FIELDS).properties().forEach(entry -> fields.put(entry.getKey(), readField(entry.getValue())));
            cases.add(new CaseResult(node.path(CASE_INDEX).asInt(), node.path(LABEL).asText(null), fields));
        }
        return new ExtractionResult(root.path(DOCUMENT_ID).asText(), root.path(CATALOG_VERSION).asText(null),
                root.path(MODEL).asText(null), flags, cases);
    }

    private FieldResult readField(JsonNode node) {
        FieldStatus status = FieldStatus.fromCode(node.path(STATUS).asText())
                .orElseThrow(() -> new IllegalArgumentException("Invalid field status " + node.path(STATUS)));
        List<Span> provenance = new ArrayList<>();
        for (JsonNode item : node.path(PROVENANCE)) {
            OptionalInt page = item.has(PAGE) ? OptionalInt.of(item.get(PAGE).asInt()) : OptionalInt.empty();
            provenance.add(new Span(item.path(QUOTE).asText(), item.path(START).asInt(), item.path(END).asInt(), page));
        }
        return new FieldResult(status, node.get(VALUE),
                Confidence.fromCode(node.path(CONFIDENCE).asText()).orElse(null), provenance,
                node.path(NOTE).asText(null));
    }
}
