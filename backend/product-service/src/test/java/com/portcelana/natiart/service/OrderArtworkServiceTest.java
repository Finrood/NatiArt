package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.CustomerUploadRepository;
import com.portcelana.natiart.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class OrderArtworkServiceTest {
    @Mock
    private OrderManager orderManager;
    @Mock
    private CustomerUploadRepository uploads;
    @Mock
    private StorageService storage;
    @InjectMocks
    private OrderArtworkService service;

    private CustomerOrderItem item() {
        return new CustomerOrderItem().setPersonalization(new Personalization()
                .setPersonalizationOptions(Map.of(PersonalizationOption.CUSTOM_IMAGE, "upload-1")));
    }

    private void order(CustomerOrderItem item) {
        when(orderManager.getOrderById("order-1"))
                .thenReturn(new CustomerOrder().setOwnerExternalId("owner").setItems(List.of(item)));
    }

    @Test
    void openArtwork_readsClaimedArtworkFromSelectedOrderLine() {
        final CustomerOrderItem item = item();
        order(item);
        final CustomerUpload upload = new CustomerUpload("owner", "file:customer-uploads/art.webp", "image/webp", 3)
                .setConsumedAt(Instant.now());
        when(uploads.findById("upload-1")).thenReturn(Optional.of(upload));
        final ByteArrayInputStream bytes = new ByteArrayInputStream(new byte[] {1, 2, 3});
        when(storage.openFile(URI.create(upload.getStorageUri()))).thenReturn(bytes);
        assertSame(bytes, service.openArtworkOrDie("order-1", item.getId()));
    }

    @Test
    void openArtwork_rejectsLineOutsideSelectedOrder() {
        order(item());
        assertThrows(ResourceNotFoundException.class, () -> service.openArtworkOrDie("order-1", "foreign-line"));
        verifyNoInteractions(uploads, storage);
    }

    @Test
    void openArtwork_rejectsForeignUploadEvenIfReferencedByLine() {
        final CustomerOrderItem item = item();
        order(item);
        when(uploads.findById("upload-1")).thenReturn(Optional.of(
                new CustomerUpload("other", "file:customer-uploads/art.webp", "image/webp", 3)
                        .setConsumedAt(Instant.now())));
        assertThrows(ResourceNotFoundException.class, () -> service.openArtworkOrDie("order-1", item.getId()));
        verifyNoInteractions(storage);
    }

    @Test
    void openArtwork_rejectsUnclaimedOrPendingUpload() {
        final CustomerOrderItem item = item();
        order(item);
        when(uploads.findById("upload-1")).thenReturn(Optional.of(
                CustomerUpload.pending("upload-1", "owner", "file:customer-uploads/art.webp", 3)));
        assertThrows(ResourceNotFoundException.class, () -> service.openArtworkOrDie("order-1", item.getId()));
        verifyNoInteractions(storage);
    }
}
