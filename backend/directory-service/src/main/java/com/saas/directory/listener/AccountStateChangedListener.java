package com.saas.directory.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.saas.directory.event.AccountStateChangedEvent;
import com.saas.directory.service.AuthCacheInvalidationClient;

@Component
public class AccountStateChangedListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountStateChangedListener.class);

    private final AuthCacheInvalidationClient invalidationClient;

    public AccountStateChangedListener(AuthCacheInvalidationClient invalidationClient) {
        this.invalidationClient = invalidationClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void accountStateChanged(AccountStateChangedEvent event) {
        try {
            if (!invalidationClient.invalidateUser(event.userId())) {
                LOGGER.warn("Product auth-cache invalidation is unconfigured; bounded expiry applies");
            }
        } catch (RuntimeException failure) {
            // Directory tokens were revoked in the committed transaction. Cached
            // product validations still expire within their configured TTL.
            LOGGER.warn("Product auth-cache invalidation failed; bounded expiry applies");
        }
    }
}
