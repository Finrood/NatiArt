package com.portcelana.natiart.dto.payment.asaas;

import com.portcelana.natiart.dto.payment.helper.PaymentStatus;

public class AsaasPaymentStatusResponse {
    private final String status;

    AsaasPaymentStatusResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}