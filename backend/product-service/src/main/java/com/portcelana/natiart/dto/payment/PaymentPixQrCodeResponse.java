package com.portcelana.natiart.dto.payment;

import java.time.LocalDateTime;

public class PaymentPixQrCodeResponse {
    private final boolean success;
    private final String encodedImage;
    private final String payload;
    private final LocalDateTime expirationDate;

    public PaymentPixQrCodeResponse(boolean success, String encodedImage, String payload, LocalDateTime expirationDate) {
        this.success = success;
        this.encodedImage = encodedImage;
        this.payload = payload;
        this.expirationDate = expirationDate;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getEncodedImage() {
        return encodedImage;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }
}