package com.portcelana.natiart.event;

import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.support.OrderStatus;

/** Published synchronously on repository save; an outbox joins the order transaction. */
public record OrderMilestoneEvent(CustomerOrder order, OrderStatus status) {}
