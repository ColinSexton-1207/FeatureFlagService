package com.example.flagship.flag.domain;

import java.util.List;

public class InvalidConfigurationException extends RuntimeException {
    private final List<Violation> violations;

    public InvalidConfigurationException(List<Violation> violations) {
        super("Flag configuration is invalid");
        this.violations = List.copyOf(violations);
    }

    public List<Violation> getViolations() { return violations; }
}
