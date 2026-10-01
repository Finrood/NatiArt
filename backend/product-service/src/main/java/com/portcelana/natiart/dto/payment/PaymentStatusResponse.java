package com.portcelana.natiart.dto.payment;

import com.portcelana.natiart.dto.payment.helper.PaymentStatus;

public class PaymentStatusResponse {
    private final String paymentId;
    private final PaymentStatus status;
    private final String orderId;

    public PaymentStatusResponse(String paymentId, PaymentStatus status) {
        this(paymentId, status, null);
    }

    public PaymentStatusResponse(String paymentId, PaymentStatus status, String orderId) {
        this.paymentId = paymentId;
        this.status = status;
        this.orderId = orderId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public PaymentStatus getStatus() {
        return status;
    }
}
