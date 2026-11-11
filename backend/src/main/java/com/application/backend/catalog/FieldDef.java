package com.application.backend.catalog;

import java.util.List;
import java.util.Optional;

public record FieldDef(
        String id,
        String label,
        String section,
        FieldType type,
        List<FieldOption> options,
        Validation validation,
        Optional<String> guidance) {

    public List<String> optionCodes() {
        return options.stream().map(FieldOption::code).toList();
    }
}
