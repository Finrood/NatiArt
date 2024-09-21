package com.portcelana.natiart.dto.payment.asaas;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portcelana.natiart.dto.payment.PaymentCreationRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AsaasPaymentCreationRequest {
    @JsonProperty(required = true)
    private String customer;

    @JsonProperty(required = true)
    private String billingType;

    @JsonProperty(required = true)
    private Double value;

    @JsonProperty(required = true)
    private String dueDate;

    private String description;
    private Integer daysAfterDueDateToRegistrationCancellation;
    private String externalReference;
    private Integer installmentCount;
    private Double totalValue;
    private Double installmentValue;
    private Discount discount;
    private Interest interest;
    private Fine fine;
    private Boolean postalService;
    private List<Split> split;
    private Callback callback;

    public static AsaasPaymentCreationRequest from(PaymentCreationRequest paymentCreationRequest) {
        return new AsaasPaymentCreationRequest(
                paymentCreationRequest.getCustomerId(),
                paymentCreationRequest.getBillingType().name(),
                paymentCreationRequest.getValue(),
                paymentCreationRequest.getDueDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        );
    }

    private AsaasPaymentCreationRequest(String customer, String billingType, Double value, String dueDate) {
        this.customer = customer;
        this.billingType = billingType;
        this.value = value;
        this.dueDate = dueDate;
    }

    public String getCustomer() {
        return customer;
    }

    public AsaasPaymentCreationRequest setCustomer(String customer) {
        this.customer = customer;
        return this;
    }

    public String getBillingType() {
        return billingType;
    }

    public AsaasPaymentCreationRequest setBillingType(String billingType) {
        this.billingType = billingType;
        return this;
    }

    public Double getValue() {
        return value;
    }

    public AsaasPaymentCreationRequest setValue(Double value) {
        this.value = value;
        return this;
    }

    public String getDueDate() {
        return dueDate;
    }

    public AsaasPaymentCreationRequest setDueDate(String dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public AsaasPaymentCreationRequest setDescription(String description) {
        this.description = description;
        return this;
    }

    public Integer getDaysAfterDueDateToRegistrationCancellation() {
        return daysAfterDueDateToRegistrationCancellation;
    }

    public AsaasPaymentCreationRequest setDaysAfterDueDateToRegistrationCancellation(Integer daysAfterDueDateToRegistrationCancellation) {
        this.daysAfterDueDateToRegistrationCancellation = daysAfterDueDateToRegistrationCancellation;
        return this;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public AsaasPaymentCreationRequest setExternalReference(String externalReference) {
        this.externalReference = externalReference;
        return this;
    }

    public Integer getInstallmentCount() {
        return installmentCount;
    }

    public AsaasPaymentCreationRequest setInstallmentCount(Integer installmentCount) {
        this.installmentCount = installmentCount;
        return this;
    }

    public Double getTotalValue() {
        return totalValue;
    }

    public AsaasPaymentCreationRequest setTotalValue(Double totalValue) {
        this.totalValue = totalValue;
        return this;
    }

    public Double getInstallmentValue() {
        return installmentValue;
    }

    public AsaasPaymentCreationRequest setInstallmentValue(Double installmentValue) {
        this.installmentValue = installmentValue;
        return this;
    }

    public Discount getDiscount() {
        return discount;
    }

    public AsaasPaymentCreationRequest setDiscount(Discount discount) {
        this.discount = discount;
        return this;
    }

    public Interest getInterest() {
        return interest;
    }

    public AsaasPaymentCreationRequest setInterest(Interest interest) {
        this.interest = interest;
        return this;
    }

    public Fine getFine() {
        return fine;
    }

    public AsaasPaymentCreationRequest setFine(Fine fine) {
        this.fine = fine;
        return this;
    }

    public Boolean getPostalService() {
        return postalService;
    }

    public AsaasPaymentCreationRequest setPostalService(Boolean postalService) {
        this.postalService = postalService;
        return this;
    }

    public List<Split> getSplit() {
        return split;
    }

    public AsaasPaymentCreationRequest setSplit(List<Split> split) {
        this.split = split;
        return this;
    }

    public Callback getCallback() {
        return callback;
    }

    public AsaasPaymentCreationRequest setCallback(Callback callback) {
        this.callback = callback;
        return this;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Discount {
        private Double value;
        private Integer dueDateLimitDays;
        private String type;

        public Double getValue() {
            return value;
        }

        public Discount setValue(Double value) {
            this.value = value;
            return this;
        }

        public Integer getDueDateLimitDays() {
            return dueDateLimitDays;
        }

        public Discount setDueDateLimitDays(Integer dueDateLimitDays) {
            this.dueDateLimitDays = dueDateLimitDays;
            return this;
        }

        public String getType() {
            return type;
        }

        public Discount setType(String type) {
            this.type = type;
            return this;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Interest {
        private Double value;

        public Double getValue() {
            return value;
        }

        public Interest setValue(Double value) {
            this.value = value;
            return this;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Fine {
        private Double value;
        private String type;

        public Double getValue() {
            return value;
        }

        public Fine setValue(Double value) {
            this.value = value;
            return this;
        }

        public String getType() {
            return type;
        }

        public Fine setType(String type) {
            this.type = type;
            return this;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Split {
        private String walletId;
        private Double fixedValue;
        private Double percentualValue;
        private Double totalFixedValue;
        private String externalReference;
        private String description;

        public String getWalletId() {
            return walletId;
        }

        public Split setWalletId(String walletId) {
            this.walletId = walletId;
            return this;
        }

        public Double getFixedValue() {
            return fixedValue;
        }

        public Split setFixedValue(Double fixedValue) {
            this.fixedValue = fixedValue;
            return this;
        }

        public Double getPercentualValue() {
            return percentualValue;
        }

        public Split setPercentualValue(Double percentualValue) {
            this.percentualValue = percentualValue;
            return this;
        }

        public Double getTotalFixedValue() {
            return totalFixedValue;
        }

        public Split setTotalFixedValue(Double totalFixedValue) {
            this.totalFixedValue = totalFixedValue;
            return this;
        }

        public String getExternalReference() {
            return externalReference;
        }

        public Split setExternalReference(String externalReference) {
            this.externalReference = externalReference;
            return this;
        }

        public String getDescription() {
            return description;
        }

        public Split setDescription(String description) {
            this.description = description;
            return this;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Callback {
        private String successUrl;
        private Boolean autoRedirect;

        public String getSuccessUrl() {
            return successUrl;
        }

        public Callback setSuccessUrl(String successUrl) {
            this.successUrl = successUrl;
            return this;
        }

        public Boolean getAutoRedirect() {
            return autoRedirect;
        }

        public Callback setAutoRedirect(Boolean autoRedirect) {
            this.autoRedirect = autoRedirect;
            return this;
        }
    }
}
