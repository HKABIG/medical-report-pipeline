package com.application.backend.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.application.backend.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Checks a value against its catalog field: type, options and the validation keys. Generic over every field.
 * Returns the reason a value is rejected, or empty when it is valid.
 */
@Component
public class CatalogValidator {

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final Set<String> exclusiveOptionCodes;

    @Autowired
    public CatalogValidator(AppProperties properties) {
        this(properties.pipeline().exclusiveOptionCodes());
    }

    public CatalogValidator(Set<String> exclusiveOptionCodes) {
        this.exclusiveOptionCodes = Set.copyOf(exclusiveOptionCodes);
    }

    public Optional<String> validate(FieldDef field, JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return Optional.of("no value");
        }
        return switch (field.type()) {
            case STRING -> validateString(field, value);
            case INTEGER, NUMBER -> validateNumber(field, value);
            case DATE -> validateDate(value);
            case CODED -> validateCoded(field, value);
            case CODED_MULTI -> validateCodedMulti(field, value);
            case STRING_LIST -> validateStringList(value);
        };
    }

    private Optional<String> validateString(FieldDef field, JsonNode value) {
        if (!value.isTextual()) {
            return Optional.of("text expected");
        }
        Pattern pattern = field.validation().pattern();
        if (pattern != null && !pattern.matcher(value.asText()).find()) {
            return Optional.of("does not match pattern " + pattern.pattern());
        }
        return Optional.empty();
    }

    private Optional<String> validateNumber(FieldDef field, JsonNode value) {
        if (!value.isNumber()) {
            return Optional.of("number expected");
        }
        BigDecimal number = value.decimalValue();
        if (field.type() == FieldType.INTEGER && number.stripTrailingZeros().scale() > 0) {
            return Optional.of("whole number expected");
        }
        Validation validation = field.validation();
        if (validation.min() != null && number.compareTo(validation.min()) < 0) {
            return Optional.of("below minimum " + validation.min().toPlainString());
        }
        if (validation.max() != null && number.compareTo(validation.max()) > 0) {
            return Optional.of("above maximum " + validation.max().toPlainString());
        }
        return Optional.empty();
    }

    private Optional<String> validateDate(JsonNode value) {
        if (!value.isTextual() || !ISO_DATE.matcher(value.asText()).matches()) {
            return Optional.of("ISO date YYYY-MM-DD expected");
        }
        try {
            LocalDate.parse(value.asText());
        } catch (DateTimeParseException e) {
            return Optional.of("not a calendar date");
        }
        return Optional.empty();
    }

    private Optional<String> validateCoded(FieldDef field, JsonNode value) {
        if (!value.isTextual() || !field.optionCodes().contains(value.asText())) {
            return Optional.of("not one of the options " + field.optionCodes());
        }
        return Optional.empty();
    }

    private Optional<String> validateCodedMulti(FieldDef field, JsonNode value) {
        if (!value.isArray() || value.isEmpty()) {
            return Optional.of("non-empty list of option codes expected");
        }
        List<String> invalid = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || !field.optionCodes().contains(item.asText())) {
                invalid.add(item.toString());
            } else {
                codes.add(item.asText());
            }
        }
        if (!invalid.isEmpty()) {
            return Optional.of("not option codes " + invalid);
        }
        if (codes.size() > 1) {
            for (String code : codes) {
                if (exclusiveOptionCodes.contains(code)) {
                    return Optional.of("'" + code + "' must stand alone");
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> validateStringList(JsonNode value) {
        if (!value.isArray()) {
            return Optional.of("list of text expected");
        }
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank()) {
                return Optional.of("list items must be non-empty text");
            }
        }
        return Optional.empty();
    }
}
