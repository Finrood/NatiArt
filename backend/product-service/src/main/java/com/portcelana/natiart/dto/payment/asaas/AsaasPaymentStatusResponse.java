package com.portcelana.natiart.dto.payment.asaas;

public class AsaasPaymentStatusResponse {
    private final String status;

    AsaasPaymentStatusResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}