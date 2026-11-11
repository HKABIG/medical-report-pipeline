package com.application.backend.extraction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.application.backend.catalog.Catalog;
import com.application.backend.catalog.FieldDef;
import com.application.backend.config.AppProperties;
import com.application.backend.document.SourceText;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.application.backend.extraction.AnswerKeys.*;

/**
 * Builds the document output from the model's answer: keeps only cases with all four minimum elements, resolves
 * every catalog field of every kept case, and collects document flags.
 */
@Component
public class AnswerInterpreter {

    public static final String FLAG_UNSUPPORTED = "unsupported_format";
    public static final String FLAG_MULTIPLE_PATIENTS = "multiple_patients";
    public static final String FLAG_NO_ADVERSE_EVENT = "no_adverse_event";

    private final FieldResolver resolver;
    private final ObjectMapper objectMapper;
    private final Set<String> recommendedFlags;

    @Autowired
    public AnswerInterpreter(FieldResolver resolver, ObjectMapper objectMapper, AppProperties properties) {
        this(resolver, objectMapper, properties.pipeline().recommendedFlags());
    }

    public AnswerInterpreter(FieldResolver resolver, ObjectMapper objectMapper, Set<String> recommendedFlags) {
        this.resolver = resolver;
        this.objectMapper = objectMapper;
        this.recommendedFlags = Set.copyOf(recommendedFlags);
    }

    public ExtractionResult interpret(String documentId, SourceText text, Catalog catalog, String model,
            String answerText) {
        JsonNode answer;
        try {
            answer = objectMapper.readTree(stripCodeFence(answerText));
        } catch (JsonProcessingException e) {
            return new ExtractionResult(documentId, catalog.version(), model,
                    List.of(FLAG_UNSUPPORTED, "The model answer is not valid JSON: " + e.getOriginalMessage()),
                    List.of());
        }
        Set<String> flags = new LinkedHashSet<>();
        answer.path(FLAGS).forEach(flag -> {
            String code = flag.asText();
            flags.add(recommendedFlags.contains(code) ? code : "Model flag: " + code);
        });

        List<CaseResult> cases = new ArrayList<>();
        for (JsonNode modelCase : answer.path(CASES)) {
            String label = modelCase.path(LABEL).asText("").strip();
            List<String> missing = missingElements(modelCase.path(MINIMUM_ELEMENTS));
            if (!missing.isEmpty()) {
                flags.add("Case '" + label + "' not reported: no " + String.join(", no ", missing) + ".");
                continue;
            }
            int index = cases.size();
            Map<String, FieldResult> fields = new LinkedHashMap<>();
            JsonNode modelFields = modelCase.path(FIELDS);
            for (FieldDef field : catalog.fields()) {
                fields.put(field.id(), resolver.resolve(field, modelFields.get(field.id()), text));
            }
            cases.add(new CaseResult(index, label.isEmpty() ? "Case " + (index + 1) : label, fields));
        }
        if (cases.size() > 1) {
            flags.add(FLAG_MULTIPLE_PATIENTS);
        }
        String noCaseReason = answer.path(NO_CASE_REASON).asText("").strip();
        if (cases.isEmpty() && !noCaseReason.isEmpty()) {
            flags.add(noCaseReason);
        }
        answer.path(REVIEWER_NOTES).forEach(note -> {
            if (!note.asText().isBlank()) {
                flags.add(note.asText().strip());
            }
        });
        return new ExtractionResult(documentId, catalog.version(), model, List.copyOf(flags), cases);
    }

    private static List<String> missingElements(JsonNode elements) {
        List<String> missing = new ArrayList<>();
        for (MinimumElement element : MinimumElement.values()) {
            if (!elements.path(element.key()).path(PRESENT).asBoolean(false)) {
                missing.add(element.description().replaceFirst("^an? ", ""));
            }
        }
        return missing;
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence);
            }
        }
        return trimmed;
    }
}
