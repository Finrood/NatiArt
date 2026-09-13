package com.portcelana.natiart.dto.payment.asaas;

import java.math.BigDecimal;

/** Minimal Asaas webhook envelope; unknown provider fields are intentionally ignored. */
public class AsaasWebhookRequest {
    private String id;
    private String event;
    private AsaasWebhookPayment payment;

    public AsaasWebhookRequest() {}

    public String getId() {
        return id;
    }

    public AsaasWebhookRequest setId(String id) {
        this.id = id;
        return this;
    }

    public String getEvent() {
        return event;
    }

    public AsaasWebhookRequest setEvent(String event) {
        this.event = event;
        return this;
    }

    public AsaasWebhookPayment getPayment() {
        return payment;
    }

    public AsaasWebhookRequest setPayment(AsaasWebhookPayment payment) {
        this.payment = payment;
        return this;
    }

    public static class AsaasWebhookPayment {
        private String id;
        private String customer;
        private BigDecimal value;
        private String status;
        private String billingType;
        private String currency;

        public AsaasWebhookPayment() {}

        public String getId() {
            return id;
        }

        public AsaasWebhookPayment setId(String id) {
            this.id = id;
            return this;
        }

        public String getCustomer() {
            return customer;
        }

        public AsaasWebhookPayment setCustomer(String customer) {
            this.customer = customer;
            return this;
        }

        public BigDecimal getValue() {
            return value;
        }

        public AsaasWebhookPayment setValue(BigDecimal value) {
            this.value = value;
            return this;
        }

        public String getStatus() {
            return status;
        }

        public AsaasWebhookPayment setStatus(String status) {
            this.status = status;
            return this;
        }

        public String getBillingType() {
            return billingType;
        }

        public AsaasWebhookPayment setBillingType(String billingType) {
            this.billingType = billingType;
            return this;
        }

        public String getCurrency() {
            return currency;
        }

        public AsaasWebhookPayment setCurrency(String currency) {
            this.currency = currency;
            return this;
        }
    }
}
