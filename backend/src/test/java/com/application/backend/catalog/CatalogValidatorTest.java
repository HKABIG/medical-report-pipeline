package com.application.backend.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.application.backend.TestSupport;
import com.fasterxml.jackson.databind.JsonNode;

/** Exercises every field type through whichever catalog field declares it, so no field id is named here. */
class CatalogValidatorTest {

    private final Catalog catalog = TestSupport.catalog();
    private final CatalogValidator validator = TestSupport.validator();

    private FieldDef first(FieldType type) {
        return catalog.fields().stream().filter(f -> f.type() == type).findFirst().orElseThrow();
    }

    private FieldDef firstWith(FieldType type, java.util.function.Predicate<FieldDef> condition) {
        return catalog.fields().stream().filter(f -> f.type() == type && condition.test(f)).findFirst().orElseThrow();
    }

    private Optional<String> check(FieldDef field, String json) throws Exception {
        JsonNode value = TestSupport.JSON.readTree(json);
        return validator.validate(field, value);
    }

    @Test
    void stringPatternIsEnforced() throws Exception {
        FieldDef field = firstWith(FieldType.STRING, f -> f.validation().pattern() != null);
        assertThat(check(field, "\"" + "x".repeat(40) + "!\"")).isPresent();
        assertThat(check(field, "42")).contains("text expected");
    }

    @Test
    void integerRangeAndWholeness() throws Exception {
        FieldDef field = firstWith(FieldType.INTEGER, f -> f.validation().max() != null);
        assertThat(check(field, field.validation().max().toPlainString())).isEmpty();
        assertThat(check(field, field.validation().max().add(java.math.BigDecimal.ONE).toPlainString())).isPresent();
        assertThat(check(field, "3.5")).contains("whole number expected");
        assertThat(check(field, "\"54\"")).contains("number expected");
    }

    @Test
    void numberMinimum() throws Exception {
        FieldDef field = firstWith(FieldType.NUMBER, f -> f.validation().min() != null);
        assertThat(check(field, "0.25")).isEmpty();
        assertThat(check(field, "-1")).isPresent();
    }

    @Test
    void dateMustBeIsoAndReal() throws Exception {
        FieldDef field = first(FieldType.DATE);
        assertThat(check(field, "\"2026-03-04\"")).isEmpty();
        assertThat(check(field, "\"04/03/2026\"")).isPresent();
        assertThat(check(field, "\"2026-02-30\"")).contains("not a calendar date");
    }

    @Test
    void codedAcceptsOnlyOptionCodes() throws Exception {
        FieldDef field = first(FieldType.CODED);
        assertThat(check(field, "\"" + field.optionCodes().get(0) + "\"")).isEmpty();
        assertThat(check(field, "\"" + field.options().get(0).label().toUpperCase() + "-not-a-code\"")).isPresent();
    }

    @Test
    void codedMultiNeedsCodesAndKeepsExclusiveCodesAlone() throws Exception {
        FieldDef field = firstWith(FieldType.CODED_MULTI,
                f -> f.optionCodes().stream().anyMatch(TestSupport.EXCLUSIVE_CODES::contains));
        String exclusive = field.optionCodes().stream().filter(TestSupport.EXCLUSIVE_CODES::contains).findFirst()
                .orElseThrow();
        String other = field.optionCodes().stream().filter(c -> !c.equals(exclusive)).findFirst().orElseThrow();
        assertThat(check(field, "[\"" + other + "\"]")).isEmpty();
        assertThat(check(field, "[\"" + exclusive + "\"]")).isEmpty();
        assertThat(check(field, "[\"" + exclusive + "\", \"" + other + "\"]")).contains("'" + exclusive
                + "' must stand alone");
        assertThat(check(field, "[]")).isPresent();
    }

    @Test
    void stringListAllowsEmptyButNotBlankItems() throws Exception {
        FieldDef field = first(FieldType.STRING_LIST);
        assertThat(check(field, "[]")).isEmpty();
        assertThat(check(field, "[\"rash\"]")).isEmpty();
        assertThat(check(field, "[\" \"]")).isPresent();
        assertThat(check(field, "\"rash\"")).isPresent();
    }

    @Test
    void loaderRejectsUnknownValidationKeys() throws Exception {
        JsonNode raw = TestSupport.catalog().raw().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) raw.path("fields").get(0))
                .putObject("validation").put("maxLength", 3);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CatalogLoader(TestSupport.JSON).parse(raw))
                .isInstanceOf(CatalogException.class).hasMessageContaining("maxLength");
    }
}
