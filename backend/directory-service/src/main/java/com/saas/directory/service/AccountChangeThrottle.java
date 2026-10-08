package com.saas.directory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountChangeThrottle {
    private final RateLimitStore store;

    public AccountChangeThrottle(RateLimitStore store) {
        this.store = store;
    }

    /** Attempts remain counted even when the surrounding account update rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean acquire(String userId) {
        return store.tryAcquire("account-change:" + userId, 5);
    }
}
