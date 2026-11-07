package com.example.flagship.flag.application;

public class EnvironmentNotFoundException extends RuntimeException {
    public EnvironmentNotFoundException(String environmentKey) {
        super("Environment '" + environmentKey + "' was not found");
    }
}
