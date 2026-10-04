package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ProductImageOwnership;
import com.portcelana.natiart.model.ProductImageOwnership.State;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.ProductImageOwnershipRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.StorageService;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "spring.jpa.open-in-view=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductImageCleanupFairnessTest {
    @Autowired
    private ProductImageOwnershipRepository files;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private PlatformTransactionManager transactions;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void persistentOlderReferencesOrDiskFailuresDoNotStarveNewOrphansAcrossWorkerRestart(boolean diskFailures) {
        final TransactionTemplate transaction = new TransactionTemplate(transactions);
        final Instant now = Instant.now();
        final String prefix = UUID.randomUUID().toString();
        final String orphanId = transaction.execute(status -> {
            products.deleteAll();
            files.deleteAll();
            categories.deleteAll();
            final Category category = categories.save(new Category("fair-cleanup-" + prefix));
            for (int index = 0; index < 100; index++) {
                final String uri = "file:" + prefix + "/old-" + index;
                final ProductImageOwnership file = new ProductImageOwnership("original-owner", uri);
                ReflectionTestUtils.setField(file, "createdAt", now.minusSeconds(3000));
                file.requestDeletion();
                files.save(file);
                if (!diskFailures)
                    products.save(new Product("Reference " + index, BigDecimal.TEN)
                            .setCategory(category)
                            .setImages(List.of(uri)));
            }
            final ProductImageOwnership orphan =
                    new ProductImageOwnership("orphan-owner", "file:" + prefix + "/orphan");
            ReflectionTestUtils.setField(orphan, "createdAt", now.minusSeconds(2000));
            orphan.requestDeletion();
            return files.saveAndFlush(orphan).getId();
        });
        final StorageService storage = mock(StorageService.class);
        if (diskFailures)
            doAnswer(invocation -> {
                        final URI uri = invocation.getArgument(0);
                        if (uri.toString().contains("old-"))
                            throw new IllegalStateException("fixture disk unavailable");
                        return null;
                    })
                    .when(storage)
                    .delete(any(URI.class));
        for (int cycle = 0; cycle < 3; cycle++) {
            // A fresh worker proves fairness comes from persisted due dates, not process memory.
            new ProductImageLifecycle(files, products, storage, transactions).reconcile(now.plusSeconds(61L * cycle));
        }
        assertEquals(State.DELETED, files.findById(orphanId).orElseThrow().getState());
        assertEquals(
                100,
                files.findAll().stream()
                        .filter(file -> file.getState() == State.DELETE_PENDING)
                        .count());
    }
}
