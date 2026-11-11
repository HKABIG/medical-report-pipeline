package com.application.backend.catalog;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads and checks a catalog file. A catalog that cannot be understood is rejected rather than partly used. */
@Component
public class CatalogLoader {

    private static final String KEY_VERSION = "catalog_version";
    private static final String KEY_STATUSES = "statuses";
    private static final String KEY_CONFIDENCE = "confidence_levels";
    private static final String KEY_SECTIONS = "sections";
    private static final String KEY_FIELDS = "fields";
    private static final String KEY_ID = "id";
    private static final String KEY_LABEL = "label";
    private static final String KEY_SECTION = "section";
    private static final String KEY_TYPE = "type";
    private static final String KEY_OPTIONS = "options";
    private static final String KEY_CODE = "code";
    private static final String KEY_VALIDATION = "validation";
    private static final String KEY_PATTERN = "pattern";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_GUIDANCE = "guidance";

    private final ObjectMapper objectMapper;

    public CatalogLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Catalog load(Path path) {
        JsonNode root;
        try {
            root = objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new CatalogException("Cannot read catalog " + path + ": " + e.getMessage(), e);
        }
        return parse(root);
    }

    public Catalog parse(JsonNode root) {
        List<Section> sections = new ArrayList<>();
        for (JsonNode node : required(root, KEY_SECTIONS)) {
            sections.add(new Section(text(node, KEY_ID), text(node, KEY_LABEL)));
        }
        Set<String> sectionIds = new HashSet<>();
        sections.forEach(section -> sectionIds.add(section.id()));

        List<FieldDef> fields = new ArrayList<>();
        Set<String> fieldIds = new HashSet<>();
        for (JsonNode node : required(root, KEY_FIELDS)) {
            FieldDef field = parseField(node);
            if (!fieldIds.add(field.id())) {
                throw new CatalogException("Duplicate field id '" + field.id() + "'");
            }
            if (!sectionIds.contains(field.section())) {
                throw new CatalogException("Field '" + field.id() + "' names unknown section '" + field.section() + "'");
            }
            fields.add(field);
        }
        return new Catalog(
                text(root, KEY_VERSION),
                fieldNames(required(root, KEY_STATUSES)),
                strings(required(root, KEY_CONFIDENCE)),
                List.copyOf(sections),
                List.copyOf(fields),
                root);
    }

    private FieldDef parseField(JsonNode node) {
        String id = text(node, KEY_ID);
        FieldType type = FieldType.fromCode(text(node, KEY_TYPE));
        List<FieldOption> options = new ArrayList<>();
        if (node.has(KEY_OPTIONS)) {
            for (JsonNode option : node.get(KEY_OPTIONS)) {
                options.add(new FieldOption(text(option, KEY_CODE), text(option, KEY_LABEL)));
            }
        }
        if (type.hasOptions() && options.isEmpty()) {
            throw new CatalogException("Field '" + id + "' of type " + type.code() + " has no options");
        }
        return new FieldDef(
                id,
                text(node, KEY_LABEL),
                text(node, KEY_SECTION),
                type,
                List.copyOf(options),
                parseValidation(id, node.get(KEY_VALIDATION)),
                Optional.ofNullable(node.get(KEY_GUIDANCE)).map(JsonNode::asText));
    }

    private Validation parseValidation(String fieldId, JsonNode node) {
        if (node == null) {
            return Validation.NONE;
        }
        Set<String> known = Set.of(KEY_PATTERN, KEY_MIN, KEY_MAX);
        node.fieldNames().forEachRemaining(key -> {
            if (!known.contains(key)) {
                throw new CatalogException("Field '" + fieldId + "' uses unknown validation key '" + key + "'");
            }
        });
        Pattern pattern = null;
        if (node.has(KEY_PATTERN)) {
            try {
                pattern = Pattern.compile(node.get(KEY_PATTERN).asText());
            } catch (PatternSyntaxException e) {
                throw new CatalogException("Field '" + fieldId + "' has an invalid pattern", e);
            }
        }
        return new Validation(pattern, decimal(node, KEY_MIN), decimal(node, KEY_MAX));
    }

    private static BigDecimal decimal(JsonNode node, String key) {
        return node.has(key) ? node.get(key).decimalValue() : null;
    }

    private static JsonNode required(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            throw new CatalogException("Catalog is missing '" + key + "'");
        }
        return value;
    }

    private static String text(JsonNode node, String key) {
        return required(node, key).asText();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static List<String> fieldNames(JsonNode object) {
        List<String> names = new ArrayList<>();
        object.fieldNames().forEachRemaining(names::add);
        return List.copyOf(names);
    }
}
