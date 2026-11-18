package com.application.backend.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.application.backend.TestSupport;
import com.application.backend.cli.PipelineReport;
import com.application.backend.cli.PipelineService;
import com.application.backend.config.AppProperties;
import com.application.backend.config.AppProperties.PipelineCommand;
import com.application.backend.extraction.OutputStore;
import com.fasterxml.jackson.databind.JsonNode;

/** The committed recordings and outputs: replay must rebuild out/ exactly, and no recording may hold a key. */
@SpringBootTest
class CommittedOutputsTest {

    /** Google API keys, OpenAI-style secret keys, and bearer tokens. */
    private static final Pattern SECRET = Pattern.compile("AIza[0-9A-Za-z_\\-]{30,}|sk-[A-Za-z0-9_\\-]{20,}|Bearer\\s+[A-Za-z0-9._\\-]{20,}");

    @Autowired
    private PipelineService pipeline;

    @Autowired
    private AppProperties properties;

    @TempDir
    Path rebuilt;

    private static List<Path> jsonFiles(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    void replayRebuildsTheCommittedOutputsExactly() throws Exception {
        Path committed = properties.outPath();
        assertThat(committed).as("committed out/ folder").isDirectory();

        PipelineReport report = pipeline.run(PipelineCommand.REPLAY, TestSupport.catalog(),
                new OutputStore(rebuilt, TestSupport.JSON));

        assertThat(report.failures()).isEmpty();
        List<String> expectedNames = jsonFiles(committed).stream().map(p -> p.getFileName().toString()).toList();
        assertThat(jsonFiles(rebuilt).stream().map(p -> p.getFileName().toString())).containsExactlyElementsOf(expectedNames);
        for (String name : expectedNames) {
            JsonNode expected = TestSupport.JSON.readTree(committed.resolve(name).toFile());
            JsonNode actual = TestSupport.JSON.readTree(rebuilt.resolve(name).toFile());
            assertThat(actual).as(name).isEqualTo(expected);
        }
    }

    @Test
    void recordingsHoldNoKey() throws Exception {
        Path fixtures = properties.fixturesPath();
        assertThat(fixtures).as("committed fixtures/ folder").isDirectory();
        for (Path file : jsonFiles(fixtures)) {
            assertThat(SECRET.matcher(Files.readString(file)).find()).as(file.getFileName() + " holds a key").isFalse();
        }
    }
}
