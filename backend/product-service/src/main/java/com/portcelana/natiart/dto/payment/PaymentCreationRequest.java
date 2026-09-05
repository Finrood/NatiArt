package com.portcelana.natiart.dto.payment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.portcelana.natiart.dto.payment.helper.PaymentMethod;
import com.portcelana.natiart.dto.payment.helper.PaymentProcessor;

public class PaymentCreationRequest {
    private final PaymentProcessor paymentProcessor;
    private final String customerId;
    private final Double value;
    private final PaymentMethod billingType;
    private final LocalDate dueDate;

    @JsonCreator
    public PaymentCreationRequest(
            @JsonProperty("paymentProcessor") PaymentProcessor paymentProcessor,
            @JsonProperty("customerId") String customerId,
            @JsonProperty("value") Double value,
            @JsonProperty("billingType") PaymentMethod billingType) {
        this(paymentProcessor, customerId, value, billingType, Clock.systemDefaultZone());
    }

    @JsonIgnore
    PaymentCreationRequest(
            PaymentProcessor paymentProcessor,
            String customerId,
            Double value,
            PaymentMethod billingType,
            Clock clock) {
        this.paymentProcessor = paymentProcessor;
        this.customerId = customerId;
        this.value = value;
        this.billingType = billingType;

        final LocalTime minimumSwitchTime = LocalTime.of(21, 0);
        LocalDateTime now = LocalDateTime.now(clock);
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
