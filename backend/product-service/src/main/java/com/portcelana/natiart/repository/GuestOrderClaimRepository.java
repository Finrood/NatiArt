package com.portcelana.natiart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.portcelana.natiart.model.GuestOrderClaim;

public interface GuestOrderClaimRepository extends JpaRepository<GuestOrderClaim, String> {
    Optional<GuestOrderClaim> findFirstByEmailOrderByCutoffDesc(String email);
}
