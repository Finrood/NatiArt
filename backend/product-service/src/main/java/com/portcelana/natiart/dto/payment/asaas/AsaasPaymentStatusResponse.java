package com.portcelana.natiart.dto.payment.asaas;

public class AsaasPaymentStatusResponse {
    private String status;

    AsaasPaymentStatusResponse() {

    }
    
    AsaasPaymentStatusResponse(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}