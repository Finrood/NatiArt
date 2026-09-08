package com.portcelana.natiart.dto.payment;

import java.math.BigDecimal;
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
    private final BigDecimal value;
    private final PaymentMethod billingType;
    private final String orderId;
    private final LocalDate dueDate;

    @JsonCreator
    public PaymentCreationRequest(
            @JsonProperty("paymentProcessor") PaymentProcessor paymentProcessor,
            @JsonProperty("customerId") String customerId,
            @JsonProperty("value") BigDecimal value,
            @JsonProperty("billingType") PaymentMethod billingType,
            @JsonProperty("orderId") String orderId) {
        this(paymentProcessor, customerId, value, billingType, orderId, Clock.systemDefaultZone());
    }

    public PaymentCreationRequest(
            PaymentProcessor paymentProcessor, String customerId, BigDecimal value, PaymentMethod billingType) {
        this(paymentProcessor, customerId, value, billingType, Clock.systemDefaultZone());
    }

    @JsonIgnore
    PaymentCreationRequest(
            PaymentProcessor paymentProcessor,
            String customerId,
            BigDecimal value,
            PaymentMethod billingType,
            Clock clock) {
        this(paymentProcessor, customerId, value, billingType, null, clock);
    }

    @JsonIgnore
    PaymentCreationRequest(
            PaymentProcessor paymentProcessor,
            String customerId,
            BigDecimal value,
            PaymentMethod billingType,
            String orderId,
            Clock clock) {
        if (paymentProcessor == null) {
            throw new IllegalArgumentException("Payment processor is required");
        }
        if (billingType == null) {
            throw new IllegalArgumentException("Billing type is required");
        }
        // Exact decimal money: binary floating point cannot represent most BRL
        // cent values, so anything beyond cent precision is rejected instead
        // of rounded.
        if (value == null || value.signum() <= 0 || value.scale() > 2) {
            throw new IllegalArgumentException(
                    "Payment value must be a positive amount with at most two fraction digits");
        }
        this.paymentProcessor = paymentProcessor;
        this.customerId = customerId;
        this.value = value;
        this.billingType = billingType;
        this.orderId = orderId;

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

    public BigDecimal getValue() {
        return value;
    }

    public PaymentMethod getBillingType() {
        return billingType;
    }

    public String getOrderId() {
        return orderId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }
}
