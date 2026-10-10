package com.portcelana.natiart.dto;

public record OrderWorkspaceDto(
        long awaitingPayment, long readyToPrepare, long preparing, long inTransit, long failedNotifications) {}
