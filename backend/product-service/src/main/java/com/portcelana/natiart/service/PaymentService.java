package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;

public interface PaymentService {
    /**
     * Creates a payment owned by the authenticated caller. The Asaas customer is always
     * taken from {@code requesterExternalId}, never from the request body.
     */
    PaymentCreationResponse createPayment(PaymentCreationRequest paymentCreationRequest, String requesterExternalId);

    PaymentPixQrCodeResponse getPixQrCode(String paymentId, String requesterExternalId);

    PaymentStatusResponse getPaymentStatus(String paymentId, String requesterExternalId);
}
