package com.portcelana.natiart.dto.payment.asaas;

public class AsaasPaymentStatusResponse {
    private AsaasPaymentStatus status;

    AsaasPaymentStatusResponse() {

    }
    
    AsaasPaymentStatusResponse(AsaasPaymentStatus status) {
        this.status = status;
    }

    public AsaasPaymentStatus getStatus() {
        return status;
    }

    public void setStatus(AsaasPaymentStatus status) {
        this.status = status;
    }
}