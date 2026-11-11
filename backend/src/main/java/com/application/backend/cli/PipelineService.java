package com.application.backend.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.application.backend.catalog.Catalog;
import com.application.backend.config.AppProperties;
import com.application.backend.config.AppProperties.PipelineCommand;
import com.application.backend.document.DocumentRepository;
import com.application.backend.document.SourceDocument;
import com.application.backend.duplicates.DuplicateDetector;
import com.application.backend.duplicates.DuplicateGroup;
import com.application.backend.duplicates.DuplicatesJson;
import com.application.backend.extraction.ExtractionJson;
import com.application.backend.extraction.ExtractionPipeline;
import com.application.backend.extraction.ExtractionResult;
import com.application.backend.extraction.OutputStore;
import com.application.backend.llm.FixtureStore;
import com.application.backend.llm.LiveLlmClientFactory;
import com.application.backend.llm.LlmClient;
import com.application.backend.llm.LlmException;
import com.application.backend.llm.ReplayLlmClient;

/**
 * Runs every document through the pipeline. {@code extract} asks the model for documents without a recording and
 * records the exchange; {@code replay} answers only from recordings. Both rebuild every output and duplicates.json,
 * and write nothing when any document fails, so out/ is never a mix of runs.
 */
@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(PipelineService.class);
    private static final String DUPLICATE_FLAG = "possible_duplicate";

    private final AppProperties properties;
    private final DocumentRepository documents;
    private final ExtractionPipeline pipeline;
    private final ExtractionJson extractionJson;
    private final DuplicateDetector duplicateDetector;
    private final DuplicatesJson duplicatesJson;
    private final FixtureStore fixtures;
    private final ReplayLlmClient replayClient;
    private final LiveLlmClientFactory liveClient;

    public PipelineService(AppProperties properties, DocumentRepository documents, ExtractionPipeline pipeline,
            ExtractionJson extractionJson, DuplicateDetector duplicateDetector, DuplicatesJson duplicatesJson,
            FixtureStore fixtures, ReplayLlmClient replayClient, LiveLlmClientFactory liveClient) {
        this.properties = properties;
        this.documents = documents;
        this.pipeline = pipeline;
        this.extractionJson = extractionJson;
        this.duplicateDetector = duplicateDetector;
        this.duplicatesJson = duplicatesJson;
        this.fixtures = fixtures;
        this.replayClient = replayClient;
        this.liveClient = liveClient;
    }

    public PipelineReport run(PipelineCommand command, Catalog catalog, OutputStore outputs) {
        Map<String, ExtractionResult> results = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        for (SourceDocument document : documents.all()) {
            try {
                ExtractionResult result = pipeline.extract(document, catalog, clientFor(command, document));
                results.put(document.id(), result);
                log.info("{}: {} case(s), flags {}", document.id(), result.cases().size(), result.flags());
            } catch (LlmException e) {
                // A failure usually repeats for the next document (quota, model, network), so stop; a rerun resumes.
                failures.add(document.id() + ": " + e.getMessage());
                log.error("{}: {}", document.id(), e.getMessage());
                break;
            }
        }
        if (!failures.isEmpty()) {
            log.error("Stopped; nothing written to {}. Failed: {}", outputs.folder(), failures);
            return new PipelineReport(List.copyOf(results.values()), List.of(), failures);
        }
        List<DuplicateGroup> groups = duplicateDetector.detect(catalog, List.copyOf(results.values()));
        for (DuplicateGroup group : groups) {
            group.documentIds().forEach(id -> results.computeIfPresent(id, (key, r) -> r.withFlag(DUPLICATE_FLAG)));
        }
        results.values().forEach(result -> outputs.writeDocument(result.documentId(), extractionJson.write(result)));
        outputs.writeDuplicates(duplicatesJson.write(groups));
        log.info("Wrote {} output(s) and {} duplicate group(s) to {}", results.size(), groups.size(), outputs.folder());
        return new PipelineReport(List.copyOf(results.values()), groups, List.of());
    }

    private LlmClient clientFor(PipelineCommand command, SourceDocument document) {
        if (command == PipelineCommand.REPLAY || !pipeline.needsModel(document)) {
            return replayClient;
        }
        if (properties.pipeline().skipRecorded() && fixtures.hasAnswer(document.id())) {
            return replayClient;
        }
        return liveClient.get();
    }
}
