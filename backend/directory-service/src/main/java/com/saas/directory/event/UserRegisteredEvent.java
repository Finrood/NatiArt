package com.saas.directory.event;

public record UserRegisteredEvent(String username, String correlationId) {
    public UserRegisteredEvent(String username) {
        this(username, null);
    }
}
