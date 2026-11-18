package com.application.backend;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.CatalogLoader;
import com.application.backend.catalog.CatalogValidator;
import com.application.backend.config.AppProperties;
import com.application.backend.extraction.AnswerInterpreter;
import com.application.backend.extraction.FieldResolver;
import com.application.backend.extraction.QuoteLocator;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared fixtures for unit tests. Tests run from backend/, so the repository root is the parent folder. */
public final class TestSupport {

    public static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    public static final Path CATALOG = ROOT.resolve("case-intake-exercise/catalog/field_catalog.json");
    public static final Path DOCUMENTS = ROOT.resolve("case-intake-exercise/documents");
    public static final Path STRETCH_DOCUMENTS = ROOT.resolve("case-intake-exercise/documents/stretch");
    public static final Set<String> EXCLUSIVE_CODES = Set.of("none");
    public static final Set<String> RECOMMENDED_FLAGS =
            Set.of("no_adverse_event", "multiple_patients", "unsupported_format", "possible_duplicate");
    public static final ObjectMapper JSON = new ObjectMapper();

    private TestSupport() {
    }

    public static Catalog catalog() {
        return new CatalogLoader(JSON).load(CATALOG);
    }

    public static CatalogValidator validator() {
        return new CatalogValidator(EXCLUSIVE_CODES);
    }

    public static FieldResolver resolver() {
        return new FieldResolver(validator(), new QuoteLocator());
    }

    public static AnswerInterpreter interpreter() {
        return new AnswerInterpreter(resolver(), JSON, RECOMMENDED_FLAGS);
    }

    public static AppProperties.Duplicates duplicateSettings() {
        return new AppProperties.Duplicates(6, 0.75, 0.95, Set.of("report"));
    }

    public static List<Path> documentFolders() {
        return List.of(DOCUMENTS, STRETCH_DOCUMENTS);
    }
}
