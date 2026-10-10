package com.portcelana.natiart.service;

public record OrderMailMessage(String id, String leaseId, String recipient, String subject, String body) {
    @Override
    public String toString() {
        return "OrderMailMessage[redacted]";
    }
}
