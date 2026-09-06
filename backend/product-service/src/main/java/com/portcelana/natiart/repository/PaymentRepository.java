package com.portcelana.natiart.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.portcelana.natiart.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {}
