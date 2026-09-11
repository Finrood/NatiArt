package com.portcelana.natiart.service;

public interface RateLimitStore {
    boolean tryAcquire(String clientKey, int maxRequestsPerWindow);
}
