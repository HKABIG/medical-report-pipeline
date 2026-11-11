package com.application.backend.catalog;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** The validation keys a catalog field may declare. Every key is optional. */
public record Validation(Pattern pattern, BigDecimal min, BigDecimal max) {

    public static final Validation NONE = new Validation(null, null, null);
}
