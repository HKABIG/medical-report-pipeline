package com.application.backend.duplicates;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** out/duplicates.json, as defined by schema/duplicates.schema.json. */
@Component
public class DuplicatesJson {

    public static final String GROUPS = "groups";
    public static final String DOCUMENT_IDS = "document_ids";
    public static final String CONFIDENCE = "confidence";
    public static final String REASON = "reason";

    public ObjectNode write(List<DuplicateGroup> groups) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ArrayNode array = root.putArray(GROUPS);
        for (DuplicateGroup group : groups) {
            ObjectNode node = array.addObject();
            ArrayNode ids = node.putArray(DOCUMENT_IDS);
            group.documentIds().forEach(ids::add);
            node.put(CONFIDENCE, group.confidence().code());
            node.put(REASON, group.reason());
        }
        return root;
    }
}
