package com.portcelana.natiart.dto.payment;

import com.portcelana.natiart.dto.payment.helper.PaymentStatus;

public class PaymentStatusResponse {
    private final String paymentId;
    private final PaymentStatus status;

    public PaymentStatusResponse(String paymentId, PaymentStatus status) {
        this.paymentId = paymentId;
        this.status = status;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}