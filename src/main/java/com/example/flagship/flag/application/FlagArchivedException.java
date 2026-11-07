package com.example.flagship.flag.application;

public class FlagArchivedException extends RuntimeException {
    public FlagArchivedException(String flagKey) {
        super("Flag '" + flagKey + "' is archived and cannot be changed");
    }
}
