package com.portcelana.natiart.dto.payment;

import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class PaymentCreationRequest {
    private final PaymentProcessor paymentProcessor;
    private final String customerId;
    private final Double value;
    private final PaymentMethod billingType;
    private final LocalDate dueDate;

    public PaymentCreationRequest(PaymentProcessor paymentProcessor, String customerId, Double value, PaymentMethod billingType) {
        this.paymentProcessor = paymentProcessor;
        this.customerId = customerId;
        this.value = value;
        this.billingType = billingType;

        final LocalTime minimumSwitchTime = LocalTime.of(21, 0);
        LocalDateTime now = LocalDateTime.now();
        if (now.toLocalTime().isBefore(minimumSwitchTime)) {
            now = now.plusDays(1);
        }
        this.dueDate = now.toLocalDate();
    }

    public PaymentProcessor getPaymentProcessor() {
        return paymentProcessor;
    }

    public String getCustomerId() {
        return customerId;
    }

    public Double getValue() {
        return value;
    }

    public PaymentMethod getBillingType() {
        return billingType;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }
}