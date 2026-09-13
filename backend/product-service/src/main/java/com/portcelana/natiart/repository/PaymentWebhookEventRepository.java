package com.portcelana.natiart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.PaymentWebhookEvent;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, String> {
    Optional<PaymentWebhookEvent> findByProviderEventId(String providerEventId);
}
