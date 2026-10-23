package org.example.letstry.service;

public class GraphIntegrationException extends RuntimeException {

    public GraphIntegrationException(String message) {
        super(message);
    }

    public GraphIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}