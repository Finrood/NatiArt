package com.portcelana.natiart.dto.payment;

import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentStatus;

import java.time.LocalDateTime;

public class PaymentCreationResponse {
    private final String paymentId;
    private final LocalDateTime creationDate;
    private final String customerId;
    private final PaymentMethod billingType;
    private final PaymentStatus status;
    private final LocalDateTime dueDate;
    private final String invoiceUrl;
    private final String invoiceNumber;

    public PaymentCreationResponse(String paymentId, LocalDateTime creationDate, String customerId, PaymentMethod billingType, PaymentStatus status, LocalDateTime dueDate, String invoiceUrl, String invoiceNumber) {
        this.paymentId = paymentId;
        this.creationDate = creationDate;
        this.customerId = customerId;
        this.billingType = billingType;
        this.status = status;
        this.dueDate = dueDate;
        this.invoiceUrl = invoiceUrl;
        this.invoiceNumber = invoiceNumber;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public String getCustomerId() {
        return customerId;
    }

    public PaymentMethod getBillingType() {
        return billingType;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public String getInvoiceUrl() {
        return invoiceUrl;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }
}