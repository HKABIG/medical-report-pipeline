package com.application.backend.review;

import java.util.Map;
import java.util.TreeMap;

/** All decisions for one document: case index, then field id. */
public record DocumentReview(Map<Integer, Map<String, FieldReview>> cases) {

    public static DocumentReview empty() {
        return new DocumentReview(new TreeMap<>());
    }

    public int decided() {
        return cases.values().stream().mapToInt(Map::size).sum();
    }
}
