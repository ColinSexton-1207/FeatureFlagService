package com.example.flagship.flag.api;

import com.example.flagship.flag.application.FlagMetadataPatch;
import com.example.flagship.shared.error.InvalidRequestException;
import com.example.flagship.shared.error.InvalidRequestException.FieldProblem;
import com.example.flagship.shared.error.MalformedRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a PATCH body as a JSON tree, because a typed DTO cannot tell an omitted field from an
 * explicit null, and {@code "description": null} must clear the description.
 */
final class FlagPatchParser {
    private static final int MAX_NAME = 200;
    private static final int MAX_DESCRIPTION = 1000;
    private static final Set<String> IMMUTABLE = Set.of("key", "flagType");

    private FlagPatchParser() {}

    static FlagMetadataPatch parse(JsonNode body) {
        if (body == null || !body.isObject()) { throw new MalformedRequestException(); }

        List<FieldProblem> problems = new ArrayList<>();
        String name = null;
        boolean setDescription = false;
        String description = null;

        for (Map.Entry<String, JsonNode> field : body.properties()) {
            String fieldName = field.getKey();
            JsonNode value = field.getValue();
            switch (fieldName) {
                case "name" -> {
                    if (value.isNull()) {
                        problems.add(new FieldProblem("name", "must not be blank"));
                    } 
                    else {
                        String stripped = text(value).strip();
                        if (stripped.isEmpty()) {
                            problems.add(new FieldProblem("name", "must not be blank"));
                        } 
                        else if (stripped.length() > MAX_NAME) {
                            problems.add(new FieldProblem("name", "must be at most " + MAX_NAME + " characters"));
                        } 
                        else {
                            name = stripped;
                        }
                    }
                }
                case "description" -> {
                    setDescription = true;
                    if (!value.isNull()) {
                        description = text(value);
                        if (description.length() > MAX_DESCRIPTION) {
                            problems.add(new FieldProblem("description",
                                    "must be at most " + MAX_DESCRIPTION + " characters"));
                        }
                    }
                }
                default -> problems.add(new FieldProblem(fieldName, IMMUTABLE.contains(fieldName)
                        ? "cannot be changed after the flag is created"
                        : "is not a writable field for this request"));
            }
        }

        if (!problems.isEmpty()) { throw new InvalidRequestException(problems); }

        return new FlagMetadataPatch(name, setDescription, description);
    }

    /** A non-string value is the wrong JSON type, reported like any other unreadable body. */
    private static String text(JsonNode value) {
        if (!value.isTextual()) { throw new MalformedRequestException(); }

        return value.asText();
    }
}
