package com.application.backend.extraction;

/** The four elements without which a report is not a case. */
public enum MinimumElement {
    IDENTIFIABLE_PATIENT("identifiable_patient", "an identifiable patient"),
    REPORTER("reporter", "a reporter"),
    SUSPECT_DRUG("suspect_drug", "a suspect drug"),
    ADVERSE_EVENT("adverse_event", "an adverse event");

    private final String key;
    private final String description;

    MinimumElement(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String description() {
        return description;
    }
}
