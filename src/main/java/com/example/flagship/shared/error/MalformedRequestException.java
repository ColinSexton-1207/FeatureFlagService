package com.example.flagship.shared.error;

/** Body is valid JSON but not the shape the endpoint reads (wrong JSON type). Reported as MALFORMED_REQUEST. */
public class MalformedRequestException extends RuntimeException {
    public MalformedRequestException() {
        super("Request body is missing or is not valid JSON for this endpoint");
    }
}
