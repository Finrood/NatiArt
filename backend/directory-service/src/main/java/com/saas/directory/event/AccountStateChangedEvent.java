package com.saas.directory.event;

/** Account authority changed after all its directory tokens were revoked. */
public record AccountStateChangedEvent(String userId) {}
