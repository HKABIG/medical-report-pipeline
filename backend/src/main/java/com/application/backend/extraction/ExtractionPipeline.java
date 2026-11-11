package com.application.backend.extraction;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.application.backend.catalog.Catalog;
import com.application.backend.config.AppProperties;
import com.application.backend.document.DocumentFormat;
import com.application.backend.document.SourceDocument;
import com.application.backend.document.SourceText;
import com.application.backend.llm.LlmClient;
import com.application.backend.llm.LlmRequest;
import com.application.backend.llm.LlmResponse;

/** One document through the model: one request per document, the answer interpreted against the catalog. */
@Service
public class ExtractionPipeline {

    private final PromptBuilder prompts;
    private final ResponseSchemaBuilder schemas;
    private final AnswerInterpreter interpreter;
    private final int maxDocumentChars;
    private final Set<String> recommendedFlags;

    public ExtractionPipeline(PromptBuilder prompts, ResponseSchemaBuilder schemas, AnswerInterpreter interpreter,
            AppProperties properties) {
        this.prompts = prompts;
        this.schemas = schemas;
        this.interpreter = interpreter;
        this.maxDocumentChars = properties.pipeline().maxDocumentChars();
        this.recommendedFlags = properties.pipeline().recommendedFlags();
    }

    /** Whether the document needs the model at all; unsupported documents are flagged without a request. */
    public boolean needsModel(SourceDocument document) {
        return document.format() == DocumentFormat.TEXT
                && document.text().map(SourceText::length).orElse(0) <= maxDocumentChars;
    }

    public ExtractionResult extract(SourceDocument document, Catalog catalog, LlmClient client) {
        if (document.format() == DocumentFormat.IMAGE_ONLY_PDF) {
            return unsupported(document, catalog,
                    "Image-only PDF with no text layer; no vision model is configured, so it was not read.");
        }
        SourceText text = document.text().orElseThrow();
        if (text.length() > maxDocumentChars) {
            return unsupported(document, catalog, "Document has " + text.length() + " characters, above the limit of "
                    + maxDocumentChars + "; not sent to the model.");
        }
        LlmRequest request = new LlmRequest(document.id(), prompts.systemPrompt(),
                prompts.userPrompt(catalog, document.id(), text.text()), schemas.build(catalog, recommendedFlags));
        LlmResponse response = client.complete(request);
        return interpreter.interpret(document.id(), text, catalog, response.model(), response.text());
    }

    private static ExtractionResult unsupported(SourceDocument document, Catalog catalog, String reason) {
        return new ExtractionResult(document.id(), catalog.version(), null,
                List.of(AnswerInterpreter.FLAG_UNSUPPORTED, reason), List.of());
    }
}
