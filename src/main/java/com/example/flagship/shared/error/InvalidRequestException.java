package com.example.flagship.shared.error;

import java.util.List;

/**
 * A request that parsed but broke a field-level rule checked outside Bean Validation (query
 * parameters, PATCH bodies). Reported as 400 VALIDATION_FAILED with the same {@code errors} shape.
 */
public class InvalidRequestException extends RuntimeException {
    public record FieldProblem(String field, String message) {}

    private final List<FieldProblem> problems;

    public InvalidRequestException(List<FieldProblem> problems) {
        super("Request validation failed");
        this.problems = List.copyOf(problems);
    }

    public List<FieldProblem> getProblems() { return problems; }
}
