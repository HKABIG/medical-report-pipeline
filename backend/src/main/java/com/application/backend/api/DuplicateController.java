package com.application.backend.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.CatalogService;
import com.application.backend.duplicates.CaseComparison;
import com.application.backend.duplicates.DuplicateDetector;
import com.application.backend.duplicates.DuplicatesJson;
import com.application.backend.extraction.ExtractionJson;
import com.application.backend.extraction.ExtractionResult;
import com.application.backend.extraction.OutputStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

    private final OutputStore outputs;
    private final ExtractionJson extractionJson;
    private final DuplicateDetector detector;
    private final CatalogService catalogs;

    public DuplicateController(OutputStore outputs, ExtractionJson extractionJson, DuplicateDetector detector,
            CatalogService catalogs) {
        this.outputs = outputs;
        this.extractionJson = extractionJson;
        this.detector = detector;
        this.catalogs = catalogs;
    }

    @GetMapping
    public DuplicateView.Response duplicates() {
        Catalog catalog = catalogs.current();
        List<DuplicateView> views = new ArrayList<>();
        JsonNode groups = outputs.readDuplicates().map(root -> root.path(DuplicatesJson.GROUPS))
                .orElseGet(MissingNode::getInstance);
        for (JsonNode group : groups) {
            List<String> ids = new ArrayList<>();
            group.path(DuplicatesJson.DOCUMENT_IDS).forEach(id -> ids.add(id.asText()));
            List<ExtractionResult> results = ids.stream().map(this::load).toList();
            List<CaseComparison> comparisons = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                for (int j = i + 1; j < results.size(); j++) {
                    detector.bestPair(catalog, results.get(i), results.get(j)).ifPresent(comparisons::add);
                }
            }
            views.add(new DuplicateView(ids, DuplicateView.text(group.get(DuplicatesJson.CONFIDENCE)),
                    DuplicateView.text(group.get(DuplicatesJson.REASON)), comparisons));
        }
        return new DuplicateView.Response(views);
    }

    private ExtractionResult load(String id) {
        return extractionJson.read(outputs.readDocument(id)
                .orElseThrow(() -> new NotFoundException("duplicates.json names " + id + ", which has no output")));
    }
}
