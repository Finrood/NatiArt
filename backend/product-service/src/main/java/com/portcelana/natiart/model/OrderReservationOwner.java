package com.portcelana.natiart.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stable account row used to serialize pending-order creation across service instances. */
@Entity
@Table(name = "order_reservation_owner")
public class OrderReservationOwner {
    @Id
    @Column(name = "owner_external_id", length = 255, nullable = false)
    private String ownerExternalId;

    protected OrderReservationOwner() {}

    public OrderReservationOwner(String ownerExternalId) {
        this.ownerExternalId = ownerExternalId;
    }

    public String getOwnerExternalId() {
        return ownerExternalId;
    }
}
