package com.saas.directory.service;

public interface RateLimitStore {
    boolean tryAcquire(String clientKey, int maxRequestsPerWindow);
}
