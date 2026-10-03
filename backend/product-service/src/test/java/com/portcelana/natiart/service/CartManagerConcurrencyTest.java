package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.model.CartItem;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.CartItemRepository;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.ProductRepository;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import(CartManagerImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartManagerConcurrencyTest {
    @Autowired
    private CartManagerImpl cartManager;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private ProductManager productManager;

    private TransactionTemplate transactions;
    private Product product;

    @BeforeEach
    void seed() {
        transactions = new TransactionTemplate(transactionManager);
        product = transactions.execute(status -> {
            final Category category = categoryRepository.save(new Category("cart-race-" + System.nanoTime()));
            return productRepository.save(new Product("Plate", new BigDecimal("10.00")).setCategory(category));
        });
        when(productManager.getProductOrDie(product.getId())).thenReturn(product);
        when(productManager.getProduct(product.getId())).thenReturn(Optional.of(product));
    }

    private void seedLine(int quantity, boolean personalized) {
        transactions.executeWithoutResult(status -> {
            final CartItem line = new CartItem("jane", product);
            for (int i = 1; i < quantity; i++) {
                line.increaseQuantity();
            }
            if (personalized) {
                line.setPersonalization(new Personalization()
                        .setPersonalizationOptions(Map.of(PersonalizationOption.GOLDEN_BORDER, "yes")));
            }
            cartItemRepository.saveAndFlush(line);
        });
    }

    private int quantity() {
        return transactions.execute(status -> cartItemRepository
                .findCartItemByUsernameAndProductForUpdate("jane", product.getId())
                .map(CartItem::getQuantity)
                .orElse(0));
    }

    @Test
    void addHoldingRowLockThenRemoveLeavesOneUnit() throws Exception {
        seedLine(1, true);
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<?> add = workers.submit(() -> transactions.executeWithoutResult(status -> {
                cartItemRepository
                        .findCartItemByUsernameAndProductForUpdate("jane", product.getId())
                        .orElseThrow();
                locked.countDown();
                await(release);
                cartManager.createCartItem("jane", product.getId());
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            final Future<?> remove =
                    workers.submit(() -> cartManager.decreaseCartItemQuantity("jane", product.getId()));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> remove.get(200, TimeUnit.MILLISECONDS));
            release.countDown();
            add.get(5, TimeUnit.SECONDS);
            remove.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertEquals(1, quantity());
        assertEquals(1L, personalizationCount());
    }

    @Test
    void twoRemovalsDeleteLineAndPersonalization() throws Exception {
        seedLine(2, true);
        runTogether(
                () -> cartManager.decreaseCartItemQuantity("jane", product.getId()),
                () -> cartManager.decreaseCartItemQuantity("jane", product.getId()));
        assertEquals(0, quantity());
        assertEquals(0L, personalizationCount());
    }

    @Test
    void concurrentAddsAtCapAcceptOnlyOne() throws Exception {
        seedLine(99, false);
        final java.util.concurrent.atomic.AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger();
        runTogether(() -> addOrCountRejection(rejected), () -> addOrCountRejection(rejected));
        assertEquals(100, quantity());
        assertEquals(1, rejected.get());
    }

    private void addOrCountRejection(java.util.concurrent.atomic.AtomicInteger rejected) {
        try {
            cartManager.createCartItem("jane", product.getId());
        } catch (IllegalArgumentException expected) {
            rejected.incrementAndGet();
        }
    }

    private long personalizationCount() {
        final Long count = transactions.execute(status -> entityManager
                .createQuery("SELECT COUNT(p) FROM Personalization p", Long.class)
                .getSingleResult());
        return count.longValue();
    }

    private void runTogether(Runnable first, Runnable second) throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            final Future<?> a = workers.submit(() -> {
                await(start);
                first.run();
            });
            final Future<?> b = workers.submit(() -> {
                await(start);
                second.run();
            });
            start.countDown();
            a.get(5, TimeUnit.SECONDS);
            b.get(5, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for cart race gate");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
