package com.portcelana.natiart.dto.payment.asaas;

public class AsaasPaymentPixQrCodeResponse {
    private final boolean success;
    private final String encodedImage;
    private final String payload;
    private final String expirationDate; // of format 2025-09-23 23:59:59

    public AsaasPaymentPixQrCodeResponse(boolean success, String encodedImage, String payload, String expirationDate) {
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

    public String getExpirationDate() {
        return expirationDate;
    }
}