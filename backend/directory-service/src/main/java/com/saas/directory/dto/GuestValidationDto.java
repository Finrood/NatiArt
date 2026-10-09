package com.saas.directory.dto;

public record GuestValidationDto(String token, String csrfToken, boolean write) {
    @Override
    public String toString() {
        return "GuestValidationDto[redacted]";
    }
}
