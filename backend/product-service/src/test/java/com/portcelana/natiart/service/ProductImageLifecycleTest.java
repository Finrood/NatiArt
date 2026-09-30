package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.ProductImageOwnership;
import com.portcelana.natiart.model.ProductImageOwnership.State;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.ProductImageOwnershipRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageFileSystem;
import com.portcelana.natiart.storage.StorageService;
import com.portcelana.natiart.storage.StorageServiceImpl;

@DataJpaTest(properties = {"spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("local-h2")
class ProductImageLifecycleTest {
    @Autowired
    private ProductImageOwnershipRepository files;

    @Autowired
    private ProductRepository products;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private EntityManager entityManager;

    @TempDir
    Path root;

    private TransactionTemplate transaction;
    private StorageService storage;
    private ProductImageLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactions);
        final StorageServiceImpl realStorage =
                new StorageServiceImpl(List.of(new StorageFileSystem(List.of(root.toString()))));
        realStorage.setUploadScheme("file");
        storage = spy(realStorage);
        lifecycle = new ProductImageLifecycle(files, products, storage, transactions);
    }

    private InputFile input() {
        return new InputFile(new ByteArrayInputStream(new byte[] {1, 2, 3}), "image/webp", "test.webp", 3);
    }

    private URI liveFile(String owner) {
        return transaction.execute(status ->
                lifecycle.upload(owner, root.toString(), UUID.randomUUID().toString(), input()));
    }

    private ProductImageOwnership record(URI uri) {
        return files.findAll().stream()
                .filter(file -> file.getUri().equals(uri.toString()))
                .findFirst()
                .orElseThrow();
    }

    private Product product(String label, URI uri) {
        final Category category = new Category(label + " category");
        entityManager.persist(category);
        final Product product =
                new Product(label, BigDecimal.TEN).setCategory(category).setImages(List.of(uri.toString()));
        return products.save(product);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void databaseCommitFailureLeavesDurableIntentAndRestartRemovesOnlyOrphan() throws Exception {
        final URI[] uri = new URI[1];
        assertThrows(
                RuntimeException.class,
                () -> transaction.executeWithoutResult(status -> {
                    uri[0] = lifecycle.upload(
                            "rollback-owner", root.toString(), UUID.randomUUID().toString(), input());
                    product("invalid", uri[0]).setDescription("x".repeat(600));
                }));
        assertTrue(Files.exists(Path.of(uri[0])));
        assertEquals(State.STAGED, record(uri[0]).getState());
        final ProductImageLifecycle restarted = new ProductImageLifecycle(files, products, storage, transactions);
        restarted.reconcile(Instant.now().plusSeconds(3600));
        assertFalse(Files.exists(Path.of(uri[0])));
        assertEquals(State.DELETED, record(uri[0]).getState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rolledBackRemovalKeepsCommittedFileAndReference() throws Exception {
        final URI uri = liveFile("committed-owner");
        final String id =
                transaction.execute(status -> product("committed", uri).getId());
        assertThrows(
                IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> {
                    final Product product = products.findByIdWithImages(id).orElseThrow();
                    lifecycle.prepareReferences(product.getImages(), List.of());
                    product.setImages(List.of());
                    throw new IllegalStateException("rollback");
                }));
        lifecycle.reconcile(Instant.now().plusSeconds(3600));
        assertTrue(Files.exists(Path.of(uri)));
        assertEquals(State.LIVE, record(uri).getState());
        assertEquals(1, products.countImageReferences(uri.toString()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sharedProductAndPersonalizationReferencesPreventDeletionUntilBothAreRemoved() throws Exception {
        final URI uri = liveFile("shared-owner");
        final String[] ids = transaction.execute(status -> {
            final String first = product("first", uri).getId();
            final String second = product("second", uri).getId();
            final Personalization personalization = new Personalization()
                    .setPersonalizationOptions(java.util.Map.of(PersonalizationOption.CUSTOM_IMAGE, uri.toString()));
            entityManager.persist(personalization);
            return new String[] {first, second, personalization.getId()};
        });
        transaction.executeWithoutResult(status -> {
            lifecycle.prepareReferences(List.of(uri.toString()), List.of());
            products.findByIdWithImages(ids[0]).orElseThrow().setImages(List.of());
        });
        final Instant now = Instant.now().plusSeconds(3600);
        lifecycle.reconcile(now);
        assertTrue(Files.exists(Path.of(uri)));
        transaction.executeWithoutResult(status -> {
            lifecycle.prepareReferences(List.of(uri.toString()), List.of());
            products.findByIdWithImages(ids[1]).orElseThrow().setImages(List.of());
        });
        lifecycle.reconcile(now.plusSeconds(61));
        assertTrue(Files.exists(Path.of(uri)));
        transaction.executeWithoutResult(
                status -> entityManager.remove(entityManager.find(Personalization.class, ids[2])));
        lifecycle.reconcile(now.plusSeconds(122));
        assertFalse(Files.exists(Path.of(uri)));
        assertEquals(State.DELETED, record(uri).getState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedDeleteIsPersistedAndRetriedByNewWorker() throws Exception {
        final URI uri = liveFile("retry-owner");
        transaction.executeWithoutResult(status -> lifecycle.prepareReferences(List.of(uri.toString()), List.of()));
        doThrow(new IllegalStateException("disk unavailable"))
                .doCallRealMethod()
                .when(storage)
                .delete(uri);
        final Instant now = Instant.now().plusSeconds(3600);
        lifecycle.reconcile(now);
        assertTrue(Files.exists(Path.of(uri)));
        assertEquals(State.DELETE_PENDING, record(uri).getState());
        assertEquals(1, record(uri).getCleanupAttempts());
        new ProductImageLifecycle(files, products, storage, transactions).reconcile(now.plusSeconds(61));
        assertFalse(Files.exists(Path.of(uri)));
        assertEquals(State.DELETED, record(uri).getState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void liveTransactionLockPreventsWorkerDeletingAnUploadBeforeCommit() throws Exception {
        final CountDownLatch written = new CountDownLatch(1);
        final CountDownLatch commit = new CountDownLatch(1);
        final URI[] uri = new URI[1];
        try (final var executor = Executors.newFixedThreadPool(2)) {
            final var uploader = executor.submit(() -> transaction.executeWithoutResult(status -> {
                uri[0] = lifecycle.upload(
                        "concurrent-owner", root.toString(), UUID.randomUUID().toString(), input());
                product("concurrent", uri[0]);
                written.countDown();
                try {
                    assertTrue(commit.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException error) {
                    throw new IllegalStateException(error);
                }
            }));
            assertTrue(written.await(5, TimeUnit.SECONDS));
            final CountDownLatch workerLockAttempted = new CountDownLatch(1);
            final ProductImageOwnershipRepository workerFiles =
                    mock(ProductImageOwnershipRepository.class, org.mockito.AdditionalAnswers.delegatesTo(files));
            doAnswer(invocation -> {
                        workerLockAttempted.countDown();
                        return files.findByIdForUpdate(invocation.getArgument(0));
                    })
                    .when(workerFiles)
                    .findByIdForUpdate(any(String.class));
            final ProductImageLifecycle worker =
                    new ProductImageLifecycle(workerFiles, products, storage, transactions);
            final var cleanup =
                    executor.submit(() -> worker.reconcile(Instant.now().plusSeconds(3600)));
            assertTrue(workerLockAttempted.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> cleanup.get(150, TimeUnit.MILLISECONDS));
            assertTrue(Files.exists(Path.of(uri[0])));
            commit.countDown();
            uploader.get(5, TimeUnit.SECONDS);
            cleanup.get(5, TimeUnit.SECONDS);
            assertTrue(Files.exists(Path.of(uri[0])));
            assertEquals(State.LIVE, record(uri[0]).getState());
            verify(storage, never()).delete(uri[0]);
        } finally {
            commit.countDown();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void secondUploadFailureLeavesFirstBatchFileRecoverable() throws Exception {
        final URI[] first = new URI[1];
        assertThrows(
                IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> {
                    first[0] = lifecycle.upload(
                            "batch-owner", root.toString(), UUID.randomUUID().toString(), input());
                    doThrow(new IllegalStateException("disk full"))
                            .when(storage)
                            .uploadFile(any(String.class), any(InputFile.class), any(String.class));
                    lifecycle.upload(
                            "batch-owner", root.toString(), UUID.randomUUID().toString(), input());
                }));
        assertTrue(Files.exists(Path.of(first[0])));
        lifecycle.reconcile(Instant.now().plusSeconds(3600));
        assertFalse(Files.exists(Path.of(first[0])));
        assertEquals(State.DELETED, record(first[0]).getState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void legacyUntrackedFilesAndForeignRetainedPathsAreNotAdoptedOrDeleted() throws Exception {
        final Path legacy = root.resolve("legacy.webp");
        Files.writeString(legacy, "existing");
        transaction.executeWithoutResult(
                status -> lifecycle.prepareReferences(List.of(legacy.toUri().toString()), List.of()));
        lifecycle.reconcile(Instant.now().plusSeconds(3600));
        assertTrue(Files.exists(legacy));
        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.executeWithoutResult(status -> lifecycle.prepareReferences(
                        List.of(), List.of(legacy.toUri().toString()))));
    }
}
