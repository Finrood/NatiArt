package com.portcelana.natiart.controller.helper;

/** A rolled-back creation request with no committed order for its owned key. */
public class OrderCreationRejectedException extends IllegalArgumentException {
    public OrderCreationRejectedException(RuntimeException cause) {
        super("Order creation was rejected", cause);
    }
}
