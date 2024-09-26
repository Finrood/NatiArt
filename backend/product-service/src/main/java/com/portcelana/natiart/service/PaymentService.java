package com.portcelana.natiart.service;

import com.portcelana.natiart.dto.payment.PaymentCreationRequest;
import com.portcelana.natiart.dto.payment.PaymentCreationResponse;
import com.portcelana.natiart.dto.payment.PaymentPixQrCodeResponse;
import com.portcelana.natiart.dto.payment.PaymentStatusResponse;

public interface PaymentService {
    PaymentCreationResponse createPayment(PaymentCreationRequest paymentCreationRequest);

    PaymentPixQrCodeResponse getPixQrCode(String paymentId);

    PaymentStatusResponse getPaymentStatus(String paymentId);
}
