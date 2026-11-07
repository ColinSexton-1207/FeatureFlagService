package com.example.flagship.flag.domain;

/** One broken rule, addressed by the request field it came from (e.g. {@code allowUsers[2]}). */
public record Violation(String field, String message) {}
