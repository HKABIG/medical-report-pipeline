package com.application.backend.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.application.backend.TestSupport;
import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.FieldDef;
import com.application.backend.catalog.FieldType;
import com.application.backend.document.SourceText;
import com.application.backend.extraction.ExtractionJson;
import com.application.backend.extraction.ExtractionResult;

/** The API over a temporary out/ holding one output with a case and one without, so it does not depend on a run. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    private static final Path workspace = createWorkspace();

    @Autowired
    private MockMvc mvc;

    private static Path createWorkspace() {
        try {
            Path folder = Files.createTempDirectory("api-test");
            writeOutputs(folder.resolve("out"));
            return folder;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeOutputs(Path out) throws Exception {
        Files.createDirectories(out);
        Catalog catalog = TestSupport.catalog();
        String d01 = Files.readString(TestSupport.DOCUMENTS.resolve("D01.txt"));
        String answer = Files.readString(Path.of("src/test/resources/answers/D01-model-answer.json"));
        ExtractionResult withCase = TestSupport.interpreter().interpret("D01", new SourceText(d01), catalog, "fake/m",
                answer);
        ExtractionResult noCase = new ExtractionResult("D06", catalog.version(), "fake/m",
                List.of("no_adverse_event"), List.of());
        ExtractionJson json = new ExtractionJson();
        TestSupport.JSON.writeValue(out.resolve("D01.json").toFile(), json.write(withCase));
        TestSupport.JSON.writeValue(out.resolve("D06.json").toFile(), json.write(noCase));
        TestSupport.JSON.writeValue(out.resolve("duplicates.json").toFile(),
                TestSupport.JSON.readTree("{\"groups\":[]}"));
    }

    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry registry) {
        registry.add("app.out-dir", () -> workspace.resolve("out").toString());
        registry.add("app.reviews-dir", () -> workspace.resolve("reviews").toString());
    }

    private static FieldDef first(FieldType type) {
        return TestSupport.catalog().fields().stream().filter(f -> f.type() == type).findFirst().orElseThrow();
    }

    @Test
    void servesTheCatalog() throws Exception {
        mvc.perform(get("/api/catalog")).andExpect(status().isOk())
                .andExpect(jsonPath("$.fields", hasSize(TestSupport.catalog().fields().size())));
    }

    @Test
    void listsDocumentsWithTitleCaseCountAndFlags() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("D01"))
                .andExpect(jsonPath("$[0].title").value("Suspected adverse drug reaction"))
                .andExpect(jsonPath("$[0].caseCount").value(1))
                .andExpect(jsonPath("$[1].caseCount").value(0))
                .andExpect(jsonPath("$[1].flags", hasItem("no_adverse_event")));
    }

    @Test
    void returnsTextAndExtractionOfOneDocument() throws Exception {
        mvc.perform(get("/api/documents/D01")).andExpect(status().isOk())
                .andExpect(jsonPath("$.text", startsWith("St Alder Infirmary")))
                .andExpect(jsonPath("$.extraction.document_id").value("D01"))
                .andExpect(jsonPath("$.format").value("TEXT"));
    }

    @Test
    void unknownDocumentIsA404Problem() throws Exception {
        mvc.perform(get("/api/documents/NOPE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No document NOPE"));
    }

    @Test
    void recordsAcceptEditAndUndoAndCountsProgress() throws Exception {
        FieldDef coded = first(FieldType.CODED);
        String url = "/api/documents/D01/cases/0/fields/" + coded.id();
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("accepted"));
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"edited\",\"value\":\"" + coded.optionCodes().get(1) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value(coded.optionCodes().get(1)));
        mvc.perform(get("/api/documents/D01"))
                .andExpect(jsonPath("$.review.cases['0']['" + coded.id() + "'].decision").value("edited"))
                .andExpect(jsonPath("$.progress.reviewed").value(1));
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"pending\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/D01")).andExpect(jsonPath("$.progress.reviewed").value(0));
    }

    @Test
    void rejectsAnEditTheCatalogDoesNotAllow() throws Exception {
        FieldDef date = first(FieldType.DATE);
        mvc.perform(patch("/api/documents/D01/cases/0/fields/" + date.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"edited\",\"value\":\"03/04/2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", startsWith("Value rejected by the catalog")));
    }

    @Test
    void rejectsUnknownFieldsAndCases() throws Exception {
        mvc.perform(patch("/api/documents/D01/cases/0/fields/no_such_field").contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"accepted\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/documents/D06/cases/0/fields/" + first(FieldType.DATE).id())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesDuplicateGroups() throws Exception {
        mvc.perform(get("/api/duplicates")).andExpect(status().isOk()).andExpect(jsonPath("$.groups", hasSize(0)));
    }
}
