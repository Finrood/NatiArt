package com.portcelana.natiart.dto.payment.asaas;

import java.time.LocalDate;
import java.util.List;

public class AsaasPaymentCreationResponse {
    private final String object;
    private final String id;
    private final LocalDate dateCreated;
    private final String customer;
    private final String paymentLink;
    private final Double value;
    private final Double netValue;
    private final Double originalValue;
    private final Double interestValue;
    private final String description;
    private final String billingType;
    private final Object pixTransaction;
    private final String status;
    private final LocalDate dueDate;
    private final LocalDate originalDueDate;
    private final LocalDate paymentDate;
    private final LocalDate clientPaymentDate;
    private final Integer installmentNumber;
    private final String invoiceUrl;
    private final String invoiceNumber;
    private final String externalReference;
    private final boolean deleted;
    private final boolean anticipated;
    private final boolean anticipable;
    private final LocalDate creditDate;
    private final LocalDate estimatedCreditDate;
    private final String transactionReceiptUrl;
    private final String nossoNumero;
    private final String bankSlipUrl;
    private final LocalDate lastInvoiceViewedDate;
    private final LocalDate lastBankSlipViewedDate;
    private final Discount discount;
    private final Fine fine;
    private final Interest interest;
    private final boolean postalService;
    private final Object custody;
    private final List<Object> refunds;

    private AsaasPaymentCreationResponse(String object, String id, LocalDate dateCreated, String customer, String paymentLink, Double value, Double netValue, Double originalValue, Double interestValue, String description, String billingType, Object pixTransaction, String status, LocalDate dueDate, LocalDate originalDueDate, LocalDate paymentDate, LocalDate clientPaymentDate, Integer installmentNumber, String invoiceUrl, String invoiceNumber, String externalReference, boolean deleted, boolean anticipated, boolean anticipable, LocalDate creditDate, LocalDate estimatedCreditDate, String transactionReceiptUrl, String nossoNumero, String bankSlipUrl, LocalDate lastInvoiceViewedDate, LocalDate lastBankSlipViewedDate, Discount discount, Fine fine, Interest interest, boolean postalService, Object custody, List<Object> refunds) {
        this.object = object;
        this.id = id;
        this.dateCreated = dateCreated;
        this.customer = customer;
        this.paymentLink = paymentLink;
        this.value = value;
        this.netValue = netValue;
        this.originalValue = originalValue;
        this.interestValue = interestValue;
        this.description = description;
        this.billingType = billingType;
        this.pixTransaction = pixTransaction;
        this.status = status;
        this.dueDate = dueDate;
        this.originalDueDate = originalDueDate;
        this.paymentDate = paymentDate;
        this.clientPaymentDate = clientPaymentDate;
        this.installmentNumber = installmentNumber;
        this.invoiceUrl = invoiceUrl;
        this.invoiceNumber = invoiceNumber;
        this.externalReference = externalReference;
        this.deleted = deleted;
        this.anticipated = anticipated;
        this.anticipable = anticipable;
        this.creditDate = creditDate;
        this.estimatedCreditDate = estimatedCreditDate;
        this.transactionReceiptUrl = transactionReceiptUrl;
        this.nossoNumero = nossoNumero;
        this.bankSlipUrl = bankSlipUrl;
        this.lastInvoiceViewedDate = lastInvoiceViewedDate;
        this.lastBankSlipViewedDate = lastBankSlipViewedDate;
        this.discount = discount;
        this.fine = fine;
        this.interest = interest;
        this.postalService = postalService;
        this.custody = custody;
        this.refunds = refunds;
    }

    public String getObject() {
        return object;
    }

    public String getId() {
        return id;
    }

    public LocalDate getDateCreated() {
        return dateCreated;
    }

    public String getCustomer() {
        return customer;
    }

    public String getPaymentLink() {
        return paymentLink;
    }

    public Double getValue() {
        return value;
    }

    public Double getNetValue() {
        return netValue;
    }

    public Double getOriginalValue() {
        return originalValue;
    }

    public Double getInterestValue() {
        return interestValue;
    }

    public String getDescription() {
        return description;
    }

    public String getBillingType() {
        return billingType;
    }

    public Object getPixTransaction() {
        return pixTransaction;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getOriginalDueDate() {
        return originalDueDate;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public LocalDate getClientPaymentDate() {
        return clientPaymentDate;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public String getInvoiceUrl() {
        return invoiceUrl;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public boolean isAnticipated() {
        return anticipated;
    }

    public boolean isAnticipable() {
        return anticipable;
    }

    public LocalDate getCreditDate() {
        return creditDate;
    }

    public LocalDate getEstimatedCreditDate() {
        return estimatedCreditDate;
    }

    public String getTransactionReceiptUrl() {
        return transactionReceiptUrl;
    }

    public String getNossoNumero() {
        return nossoNumero;
    }

    public String getBankSlipUrl() {
        return bankSlipUrl;
    }

    public LocalDate getLastInvoiceViewedDate() {
        return lastInvoiceViewedDate;
    }

    public LocalDate getLastBankSlipViewedDate() {
        return lastBankSlipViewedDate;
    }

    public Discount getDiscount() {
        return discount;
    }

    public Fine getFine() {
        return fine;
    }

    public Interest getInterest() {
        return interest;
    }

    public boolean isPostalService() {
        return postalService;
    }

    public Object getCustody() {
        return custody;
    }

    public List<Object> getRefunds() {
        return refunds;
    }

    public static class Discount {
        private final Double value;
        private final LocalDate limitDate;
        private final Integer dueDateLimitDays;
        private final String type;

        private Discount(Double value, LocalDate limitDate, Integer dueDateLimitDays, String type) {
            this.value = value;
            this.limitDate = limitDate;
            this.dueDateLimitDays = dueDateLimitDays;
            this.type = type;
        }

        public Double getValue() {
            return value;
        }

        public LocalDate getLimitDate() {
            return limitDate;
        }

        public Integer getDueDateLimitDays() {
            return dueDateLimitDays;
        }

        public String getType() {
            return type;
        }
    }

    public static class Fine {
        private final Double value;
        private final String type;

        private Fine(Double value, String type) {
            this.value = value;
            this.type = type;
        }

        public Double getValue() {
            return value;
        }

        public String getType() {
            return type;
        }
    }

    public static class Interest {
        private final Double value;
        private final String type;

        private Interest(Double value, String type) {
            this.value = value;
            this.type = type;
        }

        public Double getValue() {
            return value;
        }

        public String getType() {
            return type;
        }
    }
}