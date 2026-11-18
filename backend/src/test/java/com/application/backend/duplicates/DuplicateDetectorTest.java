package com.application.backend.duplicates;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.application.backend.TestSupport;
import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.FieldDef;
import com.application.backend.extraction.CaseResult;
import com.application.backend.extraction.Confidence;
import com.application.backend.extraction.ExtractionResult;
import com.application.backend.extraction.FieldResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/** Builds synthetic cases from the catalog's own types, so no field id is named. */
class DuplicateDetectorTest {

    private final Catalog catalog = TestSupport.catalog();
    private final DuplicateDetector detector = new DuplicateDetector(TestSupport.duplicateSettings());

    private static JsonNode sample(FieldDef field, int variant) {
        JsonNodeFactory json = JsonNodeFactory.instance;
        return switch (field.type()) {
            case STRING -> json.textNode(variant == 0 ? "AB" : "CD");
            case INTEGER, NUMBER -> json.numberNode(40 + variant);
            case DATE -> json.textNode("2026-01-0" + (1 + variant));
            case CODED -> json.textNode(field.optionCodes().get(variant));
            case CODED_MULTI -> json.arrayNode().add(field.optionCodes().get(variant));
            case STRING_LIST -> json.arrayNode().add(variant == 0 ? "rash" : "nausea");
        };
    }

    /** A case whose fields take variant 0, except those listed, which take variant 1. */
    private ExtractionResult document(String id, List<String> different) {
        Map<String, FieldResult> fields = new LinkedHashMap<>();
        for (FieldDef field : catalog.fields()) {
            fields.put(field.id(), FieldResult.extracted(sample(field, different.contains(field.id()) ? 1 : 0),
                    Confidence.HIGH, List.of(), null));
        }
        return new ExtractionResult(id, "1.0", "m", List.of(), List.of(new CaseResult(0, id, fields)));
    }

    private List<String> idsInSection(boolean inReportSection) {
        return catalog.fields().stream()
                .filter(f -> TestSupport.duplicateSettings().ignoredSections().contains(f.section()) == inReportSection)
                .map(FieldDef::id).toList();
    }

    @Test
    void twoReportsOfTheSameCaseFromDifferentReportersAreOneGroup() {
        List<DuplicateGroup> groups = detector.detect(catalog,
                List.of(document("A", List.of()), document("B", idsInSection(true)), document("C", idsInSection(false))));
        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.documentIds()).containsExactly("A", "B");
            assertThat(group.confidence()).isEqualTo(Confidence.HIGH);
        });
    }

    @Test
    void sharingADrugAndAReactionIsNotEnough() {
        List<String> caseFields = idsInSection(false);
        List<String> mostlyDifferent = caseFields.subList(0, caseFields.size() / 2);
        assertThat(detector.detect(catalog, List.of(document("A", List.of()), document("B", mostlyDifferent))))
                .isEmpty();
    }

    @Test
    void documentsWithoutCasesAreNeverDuplicates() {
        ExtractionResult empty = new ExtractionResult("E", "1.0", null, List.of(), List.of());
        assertThat(detector.detect(catalog, List.of(empty, empty))).isEmpty();
    }
}
