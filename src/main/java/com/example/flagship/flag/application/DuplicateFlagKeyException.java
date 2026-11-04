package com.example.flagship.flag.application;

public class DuplicateFlagKeyException extends RuntimeException {

    private final String flagKey;

    public DuplicateFlagKeyException(String flagKey, Throwable cause) {
        super("A flag with key '" + flagKey + "' already exists", cause);
        this.flagKey = flagKey;
    }

    public String getFlagKey() {
        return flagKey;
    }
}
