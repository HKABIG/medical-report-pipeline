package com.application.backend.duplicates;

import java.util.List;

public record CaseComparison(CaseRef left, CaseRef right, List<FieldComparison> fields) {

    public long comparable() {
        return fields.stream().filter(f -> f.counted() && f.outcome() != Outcome.NOT_COMPARABLE).count();
    }

    public long matching() {
        return fields.stream().filter(f -> f.counted() && f.outcome() == Outcome.MATCH).count();
    }

    public double agreement() {
        return comparable() == 0 ? 0 : (double) matching() / comparable();
    }
}
