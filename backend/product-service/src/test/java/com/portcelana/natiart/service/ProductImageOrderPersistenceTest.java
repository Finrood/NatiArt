package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.dto.product.ProductImageReferenceDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CategoryRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;

@DataJpaTest(properties = "spring.sql.init.mode=never")
@Import({ProductManagerImpl.class, ProductImageLifecycle.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductImageOrderPersistenceTest {
    @Autowired
    private ProductManagerImpl manager;

    @Autowired
    private ProductRepository products;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ProductImageLifecycle lifecycle;

    @Autowired
    private com.portcelana.natiart.repository.ProductImageOwnershipRepository ownership;

    @Autowired
    private PlatformTransactionManager transactions;

    @MockitoBean
    private CategoryManager categoryManager;

    @MockitoBean
    private PackageManager packageManager;

    @MockitoBean
    private StorageService storage;

    private Category category;
    private TransactionTemplate transaction;

    @BeforeEach
    void seed() {
        transaction = new TransactionTemplate(transactions);
        category = transaction.execute(status -> categories.save(new Category("Images-" + UUID.randomUUID())));
        when(categoryManager.getCategoryOrDie(category.getId())).thenReturn(category);
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
    }

    @Test
    void interleavedUploadRetainedReorderAndRemovalSurviveSeparateTransactions() {
        final String id = transaction.execute(status -> products.save(new Product("Art", BigDecimal.TEN)
                        .setCategory(category)
                        .setImages(List.of("file:///owned/a", "file:///owned/b")))
                .getId());
        final String uploadId = UUID.randomUUID().toString();
        final InputFile upload =
                new InputFile(new ByteArrayInputStream(new byte[] {1}), "image/webp", uploadId + ".webp", 1);
        when(storage.uploadTarget(any(String.class), any(String.class))).thenReturn(URI.create("file:///owned/new"));
        when(storage.uploadFile(any(String.class), any(InputFile.class), any(String.class)))
                .thenReturn(URI.create("file:///owned/new"));
        final ProductDto dto = new ProductDto("Art", BigDecimal.TEN)
                .setWeightKg(BigDecimal.ONE)
                .setId(id)
                .setCategoryId(category.getId())
                .setImageManifest(List.of(
                        new ProductImageReferenceDto("file:///owned/b", null),
                        new ProductImageReferenceDto(null, uploadId),
                        new ProductImageReferenceDto("file:///owned/a", null)));
        transaction.executeWithoutResult(status -> manager.updateProduct(dto, List.of(upload)));
        final List<String> reloaded = transaction.execute(status ->
                List.copyOf(products.findByIdWithImages(id).orElseThrow().getImages()));
        assertEquals(List.of("file:///owned/b", "file:///owned/new", "file:///owned/a"), reloaded);
        assertEquals("file:///owned/b", reloaded.getFirst());
        dto.setImageManifest(List.of(
                new ProductImageReferenceDto("file:///owned/new", null),
                new ProductImageReferenceDto("file:///owned/b", null)));
        transaction.executeWithoutResult(status -> manager.updateProduct(dto, List.of()));
        assertEquals(
                List.of("file:///owned/new", "file:///owned/b"),
                transaction.execute(status -> List.copyOf(
                        products.findByIdWithImages(id).orElseThrow().getImages())));
    }

    @Test
    void foreignImageManifestRollsBackTheProductEdit() {
        final String id = transaction.execute(status -> products.save(new Product("Original", BigDecimal.TEN)
                        .setCategory(category)
                        .setImages(List.of("owned")))
                .getId());
        final ProductDto dto = new ProductDto("Changed", BigDecimal.ONE)
                .setWeightKg(BigDecimal.ONE)
                .setId(id)
                .setCategoryId(category.getId())
                .setImageManifest(List.of(new ProductImageReferenceDto("foreign", null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> transaction.executeWithoutResult(status -> manager.updateProduct(dto, List.of())));
        transaction.executeWithoutResult(status -> {
            final Product reloaded = products.findByIdWithImages(id).orElseThrow();
            assertEquals("Original", reloaded.getLabel());
            assertEquals(List.of("owned"), reloaded.getImages());
        });
    }

    @Test
    void failedProductTransactionLeavesManifestUploadDurablyRecoverable() {
        final String id = transaction.execute(status -> products.save(new Product("Original", BigDecimal.TEN)
                        .setCategory(category)
                        .setImages(List.of("legacy")))
                .getId());
        final String uploadId = UUID.randomUUID().toString();
        final URI target = URI.create("file:///owned/" + UUID.randomUUID());
        when(storage.uploadTarget(any(String.class), any(String.class))).thenReturn(target);
        when(storage.uploadFile(any(String.class), any(InputFile.class), any(String.class)))
                .thenReturn(target);
        final ProductDto dto = new ProductDto("Changed", BigDecimal.TEN)
                .setWeightKg(BigDecimal.ONE)
                .setId(id)
                .setCategoryId(category.getId())
                .setImageManifest(List.of(
                        new ProductImageReferenceDto(null, uploadId), new ProductImageReferenceDto("legacy", null)));
        final InputFile upload =
                new InputFile(new ByteArrayInputStream(new byte[] {1}), "image/webp", uploadId + ".webp", 1);
        assertThrows(
                IllegalStateException.class,
                () -> transaction.executeWithoutResult(status -> {
                    manager.updateProduct(dto, List.of(upload));
                    throw new IllegalStateException("failure after upload before product commit");
                }));
        assertEquals(
                List.of("legacy"),
                transaction.execute(status -> List.copyOf(
                        products.findByIdWithImages(id).orElseThrow().getImages())));
        assertEquals(
                com.portcelana.natiart.model.ProductImageOwnership.State.STAGED,
                transaction.execute(status -> ownership
                        .findByUriForUpdate(target.toString())
                        .orElseThrow()
                        .getState()));
        lifecycle.reconcile(java.time.Instant.now().plusSeconds(3600));
        org.mockito.Mockito.verify(storage).delete(target);
        assertEquals(
                com.portcelana.natiart.model.ProductImageOwnership.State.DELETED,
                transaction.execute(status -> ownership
                        .findByUriForUpdate(target.toString())
                        .orElseThrow()
                        .getState()));
    }
}
