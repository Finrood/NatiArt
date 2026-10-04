package com.portcelana.natiart.service;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.model.ProductImageOwnership;
import com.portcelana.natiart.model.ProductImageOwnership.State;
import com.portcelana.natiart.repository.ProductImageOwnershipRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;

@Service
public class ProductImageLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageLifecycle.class);
    private final ProductImageOwnershipRepository files;
    private final ProductRepository products;
    private final StorageService storage;
    private final TransactionTemplate independent;

    public ProductImageLifecycle(
            ProductImageOwnershipRepository files,
            ProductRepository products,
            StorageService storage,
            PlatformTransactionManager transactions) {
        this.files = files;
        this.products = products;
        this.storage = storage;
        this.independent = new TransactionTemplate(transactions);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Commit an intent before writing bytes; bind live ownership to the caller's product transaction. */
    public URI upload(String productId, String location, String key, InputFile input) {
        requireTransaction();
        final URI target = storage.uploadTarget(location, key);
        final String id = Objects.requireNonNull(independent.execute(
                status -> files.saveAndFlush(new ProductImageOwnership(productId, target.toString()))
                        .getId()));
        final ProductImageOwnership file = files.findByIdForUpdate(id).orElseThrow();
        if (file.getState() != State.STAGED) throw new IllegalStateException("Upload intent is no longer available");
        final URI uploaded = storage.uploadFile(location, input, key);
        if (!target.equals(uploaded)) throw new IllegalStateException("Storage returned an unexpected upload target");
        file.makeLive();
        return target;
    }

    /** Serialize references with deletion; legacy untracked files are never automatically removed. */
    public void prepareReferences(List<String> previous, List<String> retained) {
        requireTransaction();
        if (!previous.containsAll(retained))
            throw new IllegalArgumentException("Only this product's current images may be retained");
        previous.stream()
                .distinct()
                .sorted()
                .forEach(uri -> files.findByUriForUpdate(uri).ifPresent(file -> {
                    if (retained.contains(uri)) {
                        if (file.getState() == State.DELETED || file.getState() == State.STAGED)
                            throw new IllegalArgumentException("Image is no longer available");
                    } else {
                        file.requestDeletion();
                    }
                }));
    }

    /** Retry orphan/pending cleanup after restart, with a bounded batch and per-file database lock. */
    @Scheduled(fixedDelayString = "${natiart.storage.cleanup-delay-millis:60000}", initialDelay = 60000)
    public void reconcile() {
        reconcile(Instant.now());
    }

    void reconcile(Instant now) {
        final List<String> ids = files.findCleanupCandidates(
                State.DELETE_PENDING, State.STAGED, now.minusSeconds(300), now, PageRequest.of(0, 100));
        for (String id : ids) {
            try {
                independent.executeWithoutResult(status -> files.findByIdForUpdate(id)
                        .ifPresent(file -> {
                            if (file.getState() != State.DELETE_PENDING && file.getState() != State.STAGED) return;
                            file.recordAttempt(now);
                            if (products.countImageReferences(file.getUri()) > 0
                                    || files.countPersonalizationReferences(file.getUri()) > 0) return;
                            try {
                                storage.delete(URI.create(file.getUri()));
                                file.deleted();
                            } catch (RuntimeException error) {
                                LOGGER.warn("Stored product image cleanup failed; durable retry retained");
                            }
                        }));
            } catch (RuntimeException error) {
                LOGGER.warn("Product image cleanup transaction failed; durable retry retained");
            }
        }
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Product image changes require a transaction");
    }
}
