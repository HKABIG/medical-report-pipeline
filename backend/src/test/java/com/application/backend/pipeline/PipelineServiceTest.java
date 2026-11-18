package com.application.backend.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.application.backend.TestSupport;
import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.CatalogLoader;
import com.application.backend.catalog.FieldDef;
import com.application.backend.catalog.FieldType;
import com.application.backend.cli.PipelineReport;
import com.application.backend.cli.PipelineService;
import com.application.backend.config.AppProperties.PipelineCommand;
import com.application.backend.extraction.AnswerInterpreter;
import com.application.backend.extraction.CaseResult;
import com.application.backend.extraction.ExtractionResult;
import com.application.backend.extraction.FieldResolver;
import com.application.backend.extraction.FieldResult;
import com.application.backend.extraction.FieldStatus;
import com.application.backend.extraction.OutputStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Replay end to end over a small workspace: one text document with a recording, one image-only PDF. */
@SpringBootTest
class PipelineServiceTest {

    private static final Path workspace = createWorkspace();

    @Autowired
    private PipelineService pipeline;

    @Autowired
    private CatalogLoader catalogLoader;

    @TempDir
    Path out;

    private static Path createWorkspace() {
        try {
            Path root = Files.createTempDirectory("pipeline-test");
            Path docs = Files.createDirectories(root.resolve("docs"));
            Path fixtures = Files.createDirectories(root.resolve("fixtures"));
            Files.copy(TestSupport.DOCUMENTS.resolve("D01.txt"), docs.resolve("D01.txt"));
            Files.copy(TestSupport.STRETCH_DOCUMENTS.resolve("S03.pdf"), docs.resolve("S03.pdf"));
            ObjectNode fixture = TestSupport.JSON.createObjectNode();
            fixture.put("correlation_id", "D01").put("provider", "gemini").put("model", "recorded-model");
            fixture.putArray("exchanges");
            fixture.put("response_text", Files.readString(Path.of("src/test/resources/answers/D01-model-answer.json")));
            TestSupport.JSON.writeValue(fixtures.resolve("D01.json").toFile(), fixture);
            return root;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add("app.document-dirs", () -> workspace.resolve("docs").toString());
        registry.add("app.fixtures-dir", () -> workspace.resolve("fixtures").toString());
    }

    private OutputStore outputs() {
        return new OutputStore(out, TestSupport.JSON);
    }

    @Test
    void replayWritesEveryDocumentAndFlagsTheImageOnlyPdf() {
        PipelineReport report = pipeline.run(PipelineCommand.REPLAY, TestSupport.catalog(), outputs());

        assertThat(report.succeeded()).isTrue();
        assertThat(outputs().documentIds()).containsExactly("D01", "S03");
        JsonNode d01 = outputs().readDocument("D01").orElseThrow();
        assertThat(d01.path("model").asText()).isEqualTo("gemini/recorded-model");
        assertThat(d01.path("cases")).hasSize(1);
        JsonNode s03 = outputs().readDocument("S03").orElseThrow();
        assertThat(s03.path("cases")).isEmpty();
        assertThat(s03.path("flags").get(0).asText()).isEqualTo(AnswerInterpreter.FLAG_UNSUPPORTED);
        assertThat(outputs().readDuplicates()).isPresent();
    }

    @Test
    void replayTwiceGivesTheSameOutput() {
        pipeline.run(PipelineCommand.REPLAY, TestSupport.catalog(), outputs());
        JsonNode first = outputs().readDocument("D01").orElseThrow();
        pipeline.run(PipelineCommand.REPLAY, TestSupport.catalog(), outputs());
        assertThat(outputs().readDocument("D01").orElseThrow()).isEqualTo(first);
    }

    @Test
    void replayWithAChangedCatalogChecksRecordedValuesAgainstTheNewRules() {
        ObjectNode raw = TestSupport.catalog().raw().deepCopy();
        ArrayNode fields = (ArrayNode) raw.path("fields");
        ObjectNode added = fields.addObject();
        added.put("id", "batch_number").put("label", "Batch number").put("section", "drug").put("type", "string");
        FieldDef tightened = TestSupport.catalog().fields().stream()
                .filter(f -> f.type() == FieldType.STRING && f.validation().pattern() != null).findFirst().orElseThrow();
        for (JsonNode field : fields) {
            if (field.path("id").asText().equals(tightened.id())) {
                ((ObjectNode) field).putObject("validation").put("pattern", "^[0-9]+$");
            }
        }
        Catalog changed = catalogLoader.parse(raw);

        PipelineReport report = pipeline.run(PipelineCommand.REPLAY, changed, outputs());

        CaseResult result = report.results().stream().filter(r -> r.documentId().equals("D01"))
                .map(ExtractionResult::cases).findFirst().orElseThrow().get(0);
        FieldResult newField = result.fields().get("batch_number");
        assertThat(newField.status()).isEqualTo(FieldStatus.UNKNOWN);
        assertThat(newField.note()).isEqualTo(FieldResolver.NOT_COVERED);
        FieldResult failing = result.fields().get(tightened.id());
        assertThat(failing.status()).isEqualTo(FieldStatus.UNKNOWN);
        assertThat(failing.note()).contains("fails the catalog");
        assertThat(result.fields()).hasSize(changed.fields().size());
    }

    @Test
    void replayWithoutARecordingFailsAndWritesNothing() throws Exception {
        Path extra = workspace.resolve("docs/X99.txt");
        Files.writeString(extra, "An unrecorded document.");
        try {
            PipelineReport report = pipeline.run(PipelineCommand.REPLAY, TestSupport.catalog(), outputs());
            assertThat(report.succeeded()).isFalse();
            assertThat(report.failures()).singleElement().asString().contains("X99", "No recording");
            assertThat(outputs().documentIds()).isEqualTo(List.of());
        } finally {
            Files.delete(extra);
        }
    }
}
