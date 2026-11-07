package com.example.flagship.flag.application;

public class FlagNotFoundException extends RuntimeException {
    private final String flagKey;

    public FlagNotFoundException(String flagKey) {
        super("Flag '" + flagKey + "' was not found");
        this.flagKey = flagKey;
    }

    public String getFlagKey() {
        return flagKey;
    }
}
