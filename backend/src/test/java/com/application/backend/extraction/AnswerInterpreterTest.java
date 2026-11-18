package com.application.backend.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.application.backend.TestSupport;
import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.CatalogLoader;
import com.application.backend.catalog.FieldDef;
import com.application.backend.document.SourceText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AnswerInterpreterTest {

    private static final String MODEL = "provider/model-name (example)";

    private final Catalog catalog = TestSupport.catalog();
    private final AnswerInterpreter interpreter = TestSupport.interpreter();
    private final ExtractionJson json = new ExtractionJson();

    private SourceText d01() throws Exception {
        return new SourceText(Files.readString(TestSupport.DOCUMENTS.resolve("D01.txt")));
    }

    private ObjectNode answer() throws Exception {
        return (ObjectNode) TestSupport.JSON.readTree(Path.of("src/test/resources/answers/D01-model-answer.json")
                .toFile());
    }

    private ObjectNode field(ObjectNode answer, FieldDef def) {
        return (ObjectNode) answer.path("cases").get(0).path("fields").get(def.id());
    }

    private JsonNode roundTrip(ExtractionResult result) throws Exception {
        return TestSupport.JSON.readTree(TestSupport.JSON.writeValueAsString(json.write(result)));
    }

    @Test
    void reproducesTheOfficialExampleFromTheSameAnswer() throws Exception {
        ExtractionResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer().toString());
        JsonNode expected = TestSupport.JSON.readTree(
                TestSupport.ROOT.resolve("case-intake-exercise/schema/EXAMPLE-output-D01.json").toFile());
        assertThat(roundTrip(result)).isEqualTo(expected);
    }

    @Test
    void everyCatalogFieldIsPresentInCatalogOrder() throws Exception {
        ExtractionResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer().toString());
        assertThat(result.cases().get(0).fields().keySet())
                .containsExactlyElementsOf(catalog.fields().stream().map(FieldDef::id).toList());
    }

    @Test
    void derivedValuesAreNeverHighConfidence() throws Exception {
        ObjectNode answer = answer();
        FieldDef date = catalog.fields().stream().filter(f -> f.type().code().equals("date")).findFirst().orElseThrow();
        field(answer, date).put("derived", true);
        FieldResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer.toString())
                .cases().get(0).fields().get(date.id());
        assertThat(result.status()).isEqualTo(FieldStatus.EXTRACTED);
        assertThat(result.confidence()).isEqualTo(Confidence.MEDIUM);
        assertThat(result.note()).contains("capped at medium");
    }

    @Test
    void valueWithoutAQuoteInTheDocumentBecomesUnknown() throws Exception {
        ObjectNode answer = answer();
        FieldDef first = catalog.fields().get(0);
        ArrayNode quotes = field(answer, first).putArray("quotes");
        quotes.add("text that is not in the letter");
        FieldResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer.toString())
                .cases().get(0).fields().get(first.id());
        assertThat(result.status()).isEqualTo(FieldStatus.UNKNOWN);
        assertThat(result.value()).isNull();
        assertThat(result.note()).contains("none of its quotes occur");
    }

    @Test
    void valueOutsideTheCatalogBecomesUnknown() throws Exception {
        ObjectNode answer = answer();
        FieldDef coded = catalog.fields().stream().filter(f -> f.type().code().equals("coded")).findFirst()
                .orElseThrow();
        field(answer, coded).put("value", "not-an-option");
        FieldResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer.toString())
                .cases().get(0).fields().get(coded.id());
        assertThat(result.status()).isEqualTo(FieldStatus.UNKNOWN);
        assertThat(result.note()).contains("fails the catalog");
    }

    @Test
    void conflictKeepsNoValueButKeepsTheCandidatesQuotes() throws Exception {
        ObjectNode answer = answer();
        FieldDef first = catalog.fields().get(0);
        field(answer, first).put("status", "conflict").put("note", "two values");
        FieldResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer.toString())
                .cases().get(0).fields().get(first.id());
        assertThat(result.status()).isEqualTo(FieldStatus.CONFLICT);
        assertThat(result.value()).isNull();
        assertThat(result.confidence()).isNull();
        assertThat(result.provenance()).isNotEmpty();
        assertThat(result.note()).isEqualTo("two values");
    }

    @Test
    void fieldAddedToTheCatalogAfterRecordingComesOutUnknownWithANote() throws Exception {
        ObjectNode raw = catalog.raw().deepCopy();
        ((ArrayNode) raw.path("fields")).addObject()
                .put("id", "added_field").put("label", "Added").put("section", catalog.sections().get(0).id())
                .put("type", "string");
        Catalog changed = new CatalogLoader(TestSupport.JSON).parse(raw);
        FieldResult result = interpreter.interpret("D01", d01(), changed, MODEL, answer().toString())
                .cases().get(0).fields().get("added_field");
        assertThat(result.status()).isEqualTo(FieldStatus.UNKNOWN);
        assertThat(result.note()).isEqualTo(FieldResolver.NOT_COVERED);
    }

    @Test
    void caseMissingAMinimumElementIsNotReported() throws Exception {
        ObjectNode answer = answer();
        ((ObjectNode) answer.path("cases").get(0).path("minimum_elements").path("reporter")).put("present", false);
        ExtractionResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer.toString());
        assertThat(result.cases()).isEmpty();
        assertThat(result.flags()).anyMatch(flag -> flag.contains("no reporter"));
    }

    @Test
    void unknownModelFlagsBecomeFreeTextAndSeveralCasesAreFlagged() throws Exception {
        ObjectNode answer = answer();
        ((ArrayNode) answer.path("cases")).add(answer.path("cases").get(0).deepCopy());
        answer.putArray("flags").add("made_up_flag");
        ExtractionResult result = interpreter.interpret("D01", d01(), catalog, MODEL, answer.toString());
        assertThat(result.cases()).extracting(CaseResult::caseIndex).containsExactly(0, 1);
        assertThat(result.flags()).contains("Model flag: made_up_flag", AnswerInterpreter.FLAG_MULTIPLE_PATIENTS);
    }

    @Test
    void unreadableAnswerIsFlaggedUnsupportedWithNoCases() throws Exception {
        ExtractionResult result = interpreter.interpret("D01", d01(), catalog, MODEL, "{ not json");
        assertThat(result.cases()).isEmpty();
        assertThat(result.flags()).first().isEqualTo(AnswerInterpreter.FLAG_UNSUPPORTED);
    }
}
