package com.example.flagship.flag.api;

import com.example.flagship.flag.application.FlagStatusFilter;
import com.example.flagship.flag.application.ListFlagsQuery;
import com.example.flagship.shared.error.InvalidRequestException;
import com.example.flagship.shared.error.InvalidRequestException.FieldProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses list query parameters by hand so every bad value, including a non-number, is reported
 * as a VALIDATION_FAILED field error rather than a generic type-mismatch 400.
 */
final class ListFlagsParams {
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    static final int MAX_QUERY_LENGTH = 100;

    private ListFlagsParams() {}

    static ListFlagsQuery parse(String status, String q, String page, String size) {
        List<FieldProblem> problems = new ArrayList<>();

        FlagStatusFilter statusFilter = FlagStatusFilter.ACTIVE;
        if (status != null) {
            try {
                statusFilter = FlagStatusFilter.valueOf(status.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problems.add(new FieldProblem("status", "must be one of: active, archived, all"));
            }
        }

        String text = q == null || q.isBlank() ? null : q.strip();
        if (text != null && text.length() > MAX_QUERY_LENGTH) {
            problems.add(new FieldProblem("q", "must be at most " + MAX_QUERY_LENGTH + " characters"));
        }

        int pageNumber = parseInt("page", page, 0, 0, Integer.MAX_VALUE, "must be an integer of 0 or more", problems);
        int pageSize = parseInt("size", size, DEFAULT_SIZE, 1, MAX_SIZE,
                "must be an integer between 1 and " + MAX_SIZE, problems);

        if (!problems.isEmpty()) { throw new InvalidRequestException(problems); }

        return new ListFlagsQuery(statusFilter, text, pageNumber, pageSize);
    }

    private static int parseInt(String field, String raw, int defaultValue, int min, int max, String message,
                                List<FieldProblem> problems) {
        if (raw == null) { return defaultValue; }

        try {
            int value = Integer.parseInt(raw.strip());
            
            if (value >= min && value <= max) { return value; }
        } catch (NumberFormatException e) {
            // fall through to the same message as an out-of-range value
        }

        problems.add(new FieldProblem(field, message));
        
        return defaultValue;
    }
}
