package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.dto.product.ProductImageReferenceDto;
import com.portcelana.natiart.model.Category;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.repository.CartItemRepository;
import com.portcelana.natiart.repository.OrderRepository;
import com.portcelana.natiart.repository.ProductRepository;
import com.portcelana.natiart.service.support.InputValidationException;
import com.portcelana.natiart.storage.InputFile;
import com.portcelana.natiart.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class ProductManagerImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CategoryManager categoryManager;

    @Mock
    private PackageManager packageManager;

    @Mock
    private StorageService storageService;

    @Mock
    private ProductImageLifecycle imageLifecycle;

    @InjectMocks
    private ProductManagerImpl productManager;

    @Test
    void createProduct_nullLabel_throwsWithoutSaving() {
        final ProductDto dto = new ProductDto(null, BigDecimal.TEN).setCategoryId("cat-1");

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_nullPrice_throwsWithoutSaving() {
        final ProductDto dto = new ProductDto("Mug", null).setCategoryId("cat-1");

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_negativeOriginalPrice_throwsWithoutSaving() {
        final ProductDto dto = new ProductDto("Mug", new BigDecimal("-0.01")).setCategoryId("cat-1");

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_negativeMarkedPrice_throwsWithoutSaving() {
        final ProductDto dto =
                new ProductDto("Mug", BigDecimal.TEN).setCategoryId("cat-1").setMarkedPrice(new BigDecimal("-5.00"));

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_negativeStock_throwsWithoutSaving() {
        final ProductDto dto =
                new ProductDto("Mug", BigDecimal.TEN).setCategoryId("cat-1").setStockQuantity(-1);

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_zeroPrice_rejectsBeforePersistence() {
        final ProductDto dto =
                new ProductDto("Mug", BigDecimal.ZERO).setCategoryId("cat-1").setStockQuantity(0);

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void createProduct_rejectsDatabaseBoundaryViolationsBeforeLookupOrSave() {
        final List<ProductDto> invalid = List.of(
                new ProductDto("x".repeat(256), BigDecimal.ONE).setCategoryId("cat-1"),
                new ProductDto("Mug", BigDecimal.ONE)
                        .setDescription("x".repeat(256))
                        .setCategoryId("cat-1"),
                new ProductDto("Mug", new BigDecimal("10.001")).setCategoryId("cat-1"),
                new ProductDto("Mug", new BigDecimal("100000000.00")).setCategoryId("cat-1"),
                new ProductDto("Mug", BigDecimal.ONE)
                        .setMarkedPrice(BigDecimal.ZERO)
                        .setCategoryId("cat-1"));
        final List<String> fields = List.of("label", "description", "originalPrice", "originalPrice", "markedPrice");

        for (int i = 0; i < invalid.size(); i++) {
            final ProductDto dto = invalid.get(i);
            final InputValidationException failure =
                    assertThrows(InputValidationException.class, () -> productManager.createProduct(dto, null));
            assertEquals(fields.get(i), failure.getField());
        }

        verify(productRepository, never()).save(any(Product.class));
        verify(categoryManager, never()).getCategoryOrDie(any());
    }

    @Test
    void createProduct_acceptsMaximumRepresentablePriceWithoutChangingIt() {
        final Category category = new Category("Tableware");
        when(categoryManager.getCategoryOrDie("cat-1")).thenReturn(category);
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        final BigDecimal boundary = new BigDecimal("99999999.99");

        final Product created = productManager.createProduct(
                new ProductDto("Mug", boundary)
                        .setCategoryId("cat-1")
                        .setStockQuantity(0)
                        .setWeightKg(BigDecimal.ONE),
                null);

        assertEquals(boundary, created.getOriginalPrice());
    }

    @Test
    void createProduct_missingShippingWeight_rejectsBeforePersistence() {
        final ProductDto dto = new ProductDto("Mug", BigDecimal.TEN).setCategoryId("cat-1");

        assertThrows(IllegalArgumentException.class, () -> productManager.createProduct(dto, null));

        verifyNoInteractions(categoryManager, packageManager, productRepository, storageService);
    }

    @Test
    void createProduct_nullImageLists_persistsWithEmptyImages() {
        final Category category = new Category("Tableware");
        when(categoryManager.getCategoryOrDie("cat-1")).thenReturn(category);
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        final ProductDto dto = new ProductDto("  Mug  ", BigDecimal.TEN)
                .setCategoryId("cat-1")
                .setWeightKg(BigDecimal.ONE)
                .setImages(null);

        final Product created = productManager.createProduct(dto, null);

        assertEquals("Mug", created.getLabel());
        assertTrue(created.getImages().isEmpty());
        verify(storageService, never()).uploadFile(any(String.class), any(InputFile.class), any(String.class));
        verify(imageLifecycle, never())
                .upload(any(String.class), any(String.class), any(String.class), any(InputFile.class));
    }

    @Test
    void failedUploadBatchClosesAttemptedAndUnattemptedInputs() throws Exception {
        final Category category = new Category("Tableware");
        when(categoryManager.getCategoryOrDie("cat-1")).thenReturn(category);
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageLifecycle.upload(any(String.class), any(String.class), any(String.class), any(InputFile.class)))
                .thenReturn(URI.create("file:///fixture/first"))
                .thenThrow(new IllegalStateException("fixture upload failed"));
        final java.io.ByteArrayInputStream first =
                org.mockito.Mockito.spy(new java.io.ByteArrayInputStream(new byte[] {1}));
        final java.io.ByteArrayInputStream second =
                org.mockito.Mockito.spy(new java.io.ByteArrayInputStream(new byte[] {2}));
        final java.io.ByteArrayInputStream third =
                org.mockito.Mockito.spy(new java.io.ByteArrayInputStream(new byte[] {3}));
        final List<InputFile> inputs = List.of(
                new InputFile(first, "image/webp", "first", 1),
                new InputFile(second, "image/webp", "second", 1),
                new InputFile(third, "image/webp", "third", 1));
        assertThrows(
                IllegalStateException.class,
                () -> productManager.createProduct(
                        new ProductDto("Mug", BigDecimal.TEN)
                                .setCategoryId("cat-1")
                                .setWeightKg(BigDecimal.ONE),
                        inputs));
        verify(first).close();
        verify(second).close();
        verify(third).close();
        verify(imageLifecycle, times(2))
                .upload(any(String.class), any(String.class), any(String.class), any(InputFile.class));
    }

    @Test
    void createProductWritesStableImageKeyWithoutAProcessRelativeLocation() {
        final Category category = new Category("Tableware");
        final InputFile image = new InputFile(new ByteArrayInputStream(new byte[] {1}), "image/webp", "mug.webp", 1);
        when(categoryManager.getCategoryOrDie("cat-1")).thenReturn(category);
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageLifecycle.upload(any(String.class), any(String.class), any(String.class), eq(image)))
                .thenReturn(URI.create("file:products/stable.webp"));

        final Product created = productManager.createProduct(
                new ProductDto("Mug", BigDecimal.TEN).setCategoryId("cat-1").setWeightKg(BigDecimal.ONE),
                List.of(image));

        verify(imageLifecycle)
                .upload(
                        eq(created.getId()),
                        argThat(key -> key.startsWith("products/" + created.getId() + "/")),
                        any(String.class),
                        eq(image));
        assertEquals(List.of("file:products/stable.webp"), created.getImages());
    }

    @Test
    void inverseVisibility_existingProduct_flipsAtomicallyWithoutReadModifyWrite() {
        final Product product = new Product("Mug", BigDecimal.TEN);
        when(productRepository.toggleActiveById(product.getId())).thenReturn(1);
        when(productRepository.findByIdWithImages(product.getId())).thenReturn(Optional.of(product));

        final Product toggled = productManager.inverseVisibility(product.getId());

        assertEquals(product.getId(), toggled.getId());
        verify(productRepository).toggleActiveById(product.getId());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void inverseVisibility_missingProduct_throwsNotFound() {
        when(productRepository.toggleActiveById("missing")).thenReturn(0);

        assertThrows(ResourceNotFoundException.class, () -> productManager.inverseVisibility("missing"));

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getProductOrDie_nullId_throwsNotFoundWithoutQuerying() {
        assertThrows(ResourceNotFoundException.class, () -> productManager.getProductOrDie(null));

        verify(productRepository, never()).findById(any());
    }

    @Test
    void getProductWithImages_nullId_returnsEmptyWithoutQuerying() {
        assertTrue(productManager.getProductWithImages(null).isEmpty());

        verify(productRepository, never()).findByIdWithImages(any());
    }

    @Test
    void deleteProduct_nullId_throwsNotFoundWithoutDeleting() {
        assertThrows(ResourceNotFoundException.class, () -> productManager.deleteProduct(null));

        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deleteProduct_referencedByOrder_throwsWithoutDeleting() {
        final Product product = new Product("Mug", BigDecimal.TEN);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.existsByProduct(product)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> productManager.deleteProduct(product.getId()));

        verify(cartItemRepository, never()).existsByProduct(any(Product.class));
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deleteProduct_withoutReferencesDeletesProduct() {
        final Product product = new Product("Mug", BigDecimal.TEN);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.existsByProduct(product)).thenReturn(false);
        when(cartItemRepository.existsByProduct(product)).thenReturn(false);

        productManager.deleteProduct(product.getId());

        verify(productRepository).delete(product);
    }

    @Test
    void getProductImage_malformedPath_throwsBadRequestWithoutTouchingStorage() {
        final IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> productManager.getProductImage("::bad::"));

        assertEquals("Invalid image path: ::bad::", thrown.getMessage());
        verify(storageService, never()).openFile(any(URI.class));
    }

    @Test
    void getProductsOrDie_allPresent_loadsInOneQuery() {
        final Product plate = new Product("Plate", BigDecimal.TEN);
        final Product mug = new Product("Mug", BigDecimal.TEN);
        when(productRepository.findAllById(List.of(plate.getId(), mug.getId()))).thenReturn(List.of(plate, mug));

        final Map<String, Product> result = productManager.getProductsOrDie(List.of(plate.getId(), mug.getId()));

        assertEquals(2, result.size());
        assertEquals(plate.getId(), result.get(plate.getId()).getId());
        verify(productRepository, times(1)).findAllById(anyList());
    }

    @Test
    void getProductsOrDie_missingId_throwsNotFound() {
        final Product plate = new Product("Plate", BigDecimal.TEN);
        when(productRepository.findAllById(List.of(plate.getId(), "missing"))).thenReturn(List.of(plate));

        assertThrows(
                ResourceNotFoundException.class,
                () -> productManager.getProductsOrDie(List.of(plate.getId(), "missing")));
    }

    @Test
    void updateProduct_foreignRetainedImageIsRejectedBeforeUpload() {
        final Product product = new Product("Art", BigDecimal.TEN).setImages(List.of("owned"));
        when(categoryManager.getCategoryOrDie("cat")).thenReturn(new Category("Art"));
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        final ProductDto dto = new ProductDto("Art", BigDecimal.TEN)
                .setWeightKg(BigDecimal.ONE)
                .setId(product.getId())
                .setCategoryId("cat")
                .setImageManifest(List.of(new ProductImageReferenceDto("foreign", null)));
        assertEquals(
                "Retained image is not owned by this product or is duplicated",
                assertThrows(IllegalArgumentException.class, () -> productManager.updateProduct(dto, List.of()))
                        .getMessage());
        verify(storageService, never()).uploadFile(any(String.class), any(InputFile.class), any(String.class));
        verify(imageLifecycle, never())
                .upload(any(String.class), any(String.class), any(String.class), any(InputFile.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_unknownUploadReferenceIsRejectedBeforeUpload() {
        final Product product = new Product("Art", BigDecimal.TEN);
        when(categoryManager.getCategoryOrDie("cat")).thenReturn(new Category("Art"));
        when(packageManager.getPackage(null)).thenReturn(Optional.empty());
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        final ProductDto dto = new ProductDto("Art", BigDecimal.TEN)
                .setWeightKg(BigDecimal.ONE)
                .setId(product.getId())
                .setCategoryId("cat")
                .setImageManifest(List.of(new ProductImageReferenceDto(null, "unknown")));
        assertEquals(
                "Unknown or duplicated image upload reference",
                assertThrows(IllegalArgumentException.class, () -> productManager.updateProduct(dto, List.of()))
                        .getMessage());
        verify(storageService, never()).uploadFile(any(String.class), any(InputFile.class), any(String.class));
        verify(imageLifecycle, never())
                .upload(any(String.class), any(String.class), any(String.class), any(InputFile.class));
    }
}
