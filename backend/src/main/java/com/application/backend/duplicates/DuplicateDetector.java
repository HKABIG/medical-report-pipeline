package com.application.backend.duplicates;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.FieldDef;
import com.application.backend.config.AppProperties;
import com.application.backend.extraction.CaseResult;
import com.application.backend.extraction.Confidence;
import com.application.backend.extraction.ExtractionResult;
import com.application.backend.extraction.FieldResult;
import com.application.backend.extraction.FieldStatus;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Finds documents that describe the same case. Two cases from different documents are compared on every catalog
 * field both extracted, outside the sections that describe the report itself. They are the same case when enough
 * fields are comparable and nearly all agree; sharing a drug and a reaction is far from enough.
 */
@Component
public class DuplicateDetector {

    private final AppProperties.Duplicates settings;

    @Autowired
    public DuplicateDetector(AppProperties properties) {
        this(properties.duplicates());
    }

    public DuplicateDetector(AppProperties.Duplicates settings) {
        this.settings = settings;
    }

    public List<DuplicateGroup> detect(Catalog catalog, List<ExtractionResult> documents) {
        Map<String, String> parent = new TreeMap<>();
        Map<Set<String>, CaseComparison> evidence = new LinkedHashMap<>();
        for (int i = 0; i < documents.size(); i++) {
            for (int j = i + 1; j < documents.size(); j++) {
                bestPair(catalog, documents.get(i), documents.get(j)).filter(this::isDuplicate).ifPresent(pair -> {
                    union(parent, pair.left().documentId(), pair.right().documentId());
                    evidence.put(Set.of(pair.left().documentId(), pair.right().documentId()), pair);
                });
            }
        }
        Map<String, TreeSet<String>> groups = new TreeMap<>();
        parent.keySet().forEach(id -> groups.computeIfAbsent(find(parent, id), root -> new TreeSet<>()).add(id));
        List<DuplicateGroup> result = new ArrayList<>();
        for (TreeSet<String> ids : groups.values()) {
            List<CaseComparison> pairs = evidence.entrySet().stream()
                    .filter(entry -> ids.containsAll(entry.getKey())).map(Map.Entry::getValue).toList();
            double weakest = pairs.stream().mapToDouble(CaseComparison::agreement).min().orElse(0);
            Confidence confidence = weakest >= settings.highConfidenceAgreement() ? Confidence.HIGH : Confidence.MEDIUM;
            result.add(new DuplicateGroup(List.copyOf(ids), confidence, reason(catalog, pairs)));
        }
        return result;
    }

    /** The most similar pair of cases between two documents, if both have cases. */
    public Optional<CaseComparison> bestPair(Catalog catalog, ExtractionResult a, ExtractionResult b) {
        CaseComparison best = null;
        for (CaseResult left : a.cases()) {
            for (CaseResult right : b.cases()) {
                CaseComparison pair = compare(catalog, a.documentId(), left, b.documentId(), right);
                if (best == null || pair.matching() > best.matching()) {
                    best = pair;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public CaseComparison compare(Catalog catalog, String leftDocument, CaseResult left, String rightDocument,
            CaseResult right) {
        List<FieldComparison> fields = new ArrayList<>();
        for (FieldDef field : catalog.fields()) {
            boolean counted = !settings.ignoredSections().contains(field.section());
            fields.add(new FieldComparison(field.id(),
                    compare(field, left.fields().get(field.id()), right.fields().get(field.id())), counted));
        }
        return new CaseComparison(new CaseRef(leftDocument, left.caseIndex()),
                new CaseRef(rightDocument, right.caseIndex()), fields);
    }

    private boolean isDuplicate(CaseComparison pair) {
        return pair.comparable() >= settings.minComparableFields() && pair.agreement() >= settings.minAgreement();
    }

    static Outcome compare(FieldDef field, FieldResult a, FieldResult b) {
        if (a == null || b == null || a.status() != FieldStatus.EXTRACTED || b.status() != FieldStatus.EXTRACTED) {
            return Outcome.NOT_COMPARABLE;
        }
        boolean same = switch (field.type()) {
            case INTEGER, NUMBER -> decimal(a.value()).compareTo(decimal(b.value())) == 0;
            case STRING, DATE, CODED -> normalize(a.value().asText()).equals(normalize(b.value().asText()));
            case CODED_MULTI -> items(a.value()).equals(items(b.value()));
            case STRING_LIST -> {
                Set<String> left = items(a.value());
                Set<String> right = items(b.value());
                if (left.isEmpty() || right.isEmpty()) {
                    yield left.isEmpty() && right.isEmpty();
                }
                Set<String> shared = new HashSet<>(left);
                shared.retainAll(right);
                yield !shared.isEmpty();
            }
        };
        return same ? Outcome.MATCH : Outcome.DIFFER;
    }

    private static String reason(Catalog catalog, List<CaseComparison> pairs) {
        return pairs.stream().map(pair -> {
            String differ = pair.fields().stream().filter(f -> f.counted() && f.outcome() == Outcome.DIFFER)
                    .map(f -> catalog.field(f.fieldId()).map(FieldDef::label).orElse(f.fieldId()))
                    .collect(Collectors.joining(", "));
            return pair.left().documentId() + " and " + pair.right().documentId() + " agree on " + pair.matching()
                    + " of " + pair.comparable() + " comparable case fields" + (differ.isEmpty() ? "" : "; differ on "
                    + differ);
        }).collect(Collectors.joining(". ")) + ".";
    }

    private static BigDecimal decimal(JsonNode node) {
        return node.decimalValue();
    }

    private static Set<String> items(JsonNode node) {
        Set<String> items = new TreeSet<>();
        node.forEach(item -> items.add(normalize(item.asText())));
        return items;
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void union(Map<String, String> parent, String a, String b) {
        parent.putIfAbsent(a, a);
        parent.putIfAbsent(b, b);
        String rootA = find(parent, a);
        String rootB = find(parent, b);
        if (!rootA.equals(rootB)) {
            parent.put(rootB, rootA);
        }
    }

    private static String find(Map<String, String> parent, String id) {
        String current = id;
        while (!parent.get(current).equals(current)) {
            current = parent.get(current);
        }
        return current;
    }
}
