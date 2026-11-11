package com.application.backend.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The field catalog: the only place where field ids, types, options and validation rules are defined.
 * Fields keep catalog order. {@link #raw()} is the file as read, for clients that render from it.
 */
public record Catalog(
        String version,
        List<String> statuses,
        List<String> confidenceLevels,
        List<Section> sections,
        List<FieldDef> fields,
        JsonNode raw) {

    public Optional<FieldDef> field(String id) {
        return fields.stream().filter(field -> field.id().equals(id)).findFirst();
    }

    public Map<String, FieldDef> fieldsById() {
        Map<String, FieldDef> byId = new LinkedHashMap<>();
        fields.forEach(field -> byId.put(field.id(), field));
        return byId;
    }

    public Optional<Section> section(String id) {
        return sections.stream().filter(section -> section.id().equals(id)).findFirst();
    }
}
