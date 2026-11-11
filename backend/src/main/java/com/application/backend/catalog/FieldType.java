package com.application.backend.catalog;

import java.util.Arrays;

/** The value types a catalog field may declare. Each type knows its JSON code as written in the catalog. */
public enum FieldType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    DATE("date"),
    CODED("coded"),
    CODED_MULTI("coded_multi"),
    STRING_LIST("string_list");

    private final String code;

    FieldType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isList() {
        return this == CODED_MULTI || this == STRING_LIST;
    }

    public boolean hasOptions() {
        return this == CODED || this == CODED_MULTI;
    }

    public static FieldType fromCode(String code) {
        return Arrays.stream(values())
                .filter(type -> type.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new CatalogException("Unknown field type '" + code + "'"));
    }
}
