package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.dto.CustomerUploadResponse;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.repository.CustomerUploadRepository;
import com.portcelana.natiart.storage.StorageService;

@ExtendWith(MockitoExtension.class)
class CustomerUploadServiceTest {

    @Mock
    private CustomerUploadRepository customerUploadRepository;

    @Mock
    private ImageConversionService imageConversionService;

    @Mock
    private StorageService storageService;

    @Test
    void uploadStoresConvertedArtworkUnderOpaqueServerGeneratedId() throws Exception {
        final MockMultipartFile source = new MockMultipartFile("file", "source.png", "image/png", new byte[] {1});
        final MockMultipartFile converted =
                new MockMultipartFile("file", "source.webp", "image/webp", new byte[] {2, 3});
        when(imageConversionService.convertToWebP(List.of(source))).thenReturn(List.of(converted));
        when(storageService.uploadFile(anyString(), any()))
                .thenAnswer(invocation -> URI.create("file:" + invocation.getArgument(0)));
        when(customerUploadRepository.saveAndFlush(any(CustomerUpload.class))).thenAnswer(invocation -> {
            final CustomerUpload upload = invocation.getArgument(0);
            if (upload.getReadyAt() == null) {
                assertNull(upload.getConsumedAt());
            }
            return upload;
        });

        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 5_000_000L);

        final CustomerUploadResponse response = service.upload("owner-1", source);

        assertNotNull(response.getUploadId());
        final ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<CustomerUpload> upload = ArgumentCaptor.forClass(CustomerUpload.class);
        verify(storageService).uploadFile(key.capture(), any());
        verify(customerUploadRepository, times(2)).saveAndFlush(upload.capture());
        assertEquals(response.getUploadId(), upload.getValue().getId());
        assertEquals("owner-1", upload.getValue().getOwnerExternalId());
        assertEquals("image/webp", upload.getValue().getContentType());
        assertEquals("customer-uploads/" + response.getUploadId() + ".webp", key.getValue());
        assertEquals("file:" + key.getValue(), upload.getValue().getStorageUri());
        assertNotNull(upload.getValue().getReadyAt());
        final InOrder order = inOrder(customerUploadRepository, storageService);
        order.verify(customerUploadRepository).saveAndFlush(any(CustomerUpload.class));
        order.verify(storageService).uploadFile(anyString(), any());
        order.verify(customerUploadRepository).saveAndFlush(any(CustomerUpload.class));
    }

    @Test
    void uploadRemovesFileAndRecordWhenFinalCommitFails() throws Exception {
        final MockMultipartFile source = new MockMultipartFile("file", "source.png", "image/png", new byte[] {1});
        final MockMultipartFile converted =
                new MockMultipartFile("file", "source.webp", "image/webp", new byte[] {2, 3});
        when(imageConversionService.convertToWebP(List.of(source))).thenReturn(List.of(converted));
        when(storageService.uploadFile(anyString(), any()))
                .thenAnswer(invocation -> URI.create("file:" + invocation.getArgument(0)));
        when(customerUploadRepository.saveAndFlush(any(CustomerUpload.class))).thenAnswer(invocation -> {
            final CustomerUpload upload = invocation.getArgument(0);
            if (upload.getReadyAt() != null) {
                throw new IllegalStateException("database unavailable");
            }
            return upload;
        });
        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 5_000_000L);

        assertThrows(IllegalStateException.class, () -> service.upload("owner-1", source));

        final ArgumentCaptor<CustomerUpload> upload = ArgumentCaptor.forClass(CustomerUpload.class);
        verify(customerUploadRepository, times(2)).saveAndFlush(upload.capture());
        final CustomerUpload saved = upload.getValue();
        verify(storageService).deleteFile(URI.create(saved.getStorageUri()));
        verify(customerUploadRepository).deleteById(saved.getId());
    }

    @Test
    void uploadLeavesPendingRecordForRetryWhenFileCleanupFails() throws Exception {
        final MockMultipartFile source = new MockMultipartFile("file", "source.png", "image/png", new byte[] {1});
        final MockMultipartFile converted =
                new MockMultipartFile("file", "source.webp", "image/webp", new byte[] {2, 3});
        when(imageConversionService.convertToWebP(List.of(source))).thenReturn(List.of(converted));
        when(customerUploadRepository.saveAndFlush(any(CustomerUpload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storageService.uploadFile(anyString(), any())).thenThrow(new IllegalStateException("write failed"));
        doThrow(new IllegalStateException("delete failed")).when(storageService).deleteFile(any());
        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 5_000_000L);

        assertThrows(IllegalStateException.class, () -> service.upload("owner-1", source));
        verify(customerUploadRepository, never()).deleteById(anyString());
    }

    @Test
    void uploadRejectsOversizedAndNonImageFilesBeforeStorage() throws Exception {
        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 2L);

        final MockMultipartFile oversized =
                new MockMultipartFile("file", "large.png", "image/png", new byte[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> service.upload("owner-1", oversized));

        final MockMultipartFile text = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});
        assertThrows(IllegalArgumentException.class, () -> service.upload("owner-1", text));

        verifyNoUploadInteractions();
    }

    @Test
    void claimForOrderRequiresOwnerAndMarksUploadConsumed() {
        final CustomerUpload upload = new CustomerUpload("owner-1", "file:///tmp/customer.webp", "image/webp", 12L);
        when(customerUploadRepository.findByIdForUpdate(upload.getId())).thenReturn(Optional.of(upload));
        when(customerUploadRepository.save(upload)).thenReturn(upload);
        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 5_000_000L);

        assertEquals(upload, service.claimForOrder(upload.getId(), "owner-1"));
        assertNotNull(upload.getConsumedAt());
        verify(customerUploadRepository).save(upload);

        final CustomerUpload foreign = new CustomerUpload("owner-2", "file:///tmp/foreign.webp", "image/webp", 12L);
        when(customerUploadRepository.findByIdForUpdate(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThrows(ResourceNotFoundException.class, () -> service.claimForOrder(foreign.getId(), "owner-1"));
    }

    @Test
    void claimRejectsPendingAndExpiredArtwork() {
        final CustomerUpload pending = CustomerUpload.pending(
                java.util.UUID.randomUUID().toString(), "owner-1", "file:customer-uploads/pending.webp", 12L);
        when(customerUploadRepository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 5_000_000L);

        assertThrows(IllegalArgumentException.class, () -> service.claimForOrder(pending.getId(), "owner-1"));

        final CustomerUpload expired =
                spy(new CustomerUpload("owner-1", "file:customer-uploads/expired.webp", "image/webp", 12L));
        when(expired.getCreatedAt()).thenReturn(Instant.now().minus(Duration.ofDays(2)));
        when(customerUploadRepository.findByIdForUpdate(expired.getId())).thenReturn(Optional.of(expired));
        assertThrows(IllegalArgumentException.class, () -> service.claimForOrder(expired.getId(), "owner-1"));
        verify(customerUploadRepository, never()).save(any());
    }

    @Test
    void cleanupDeletesExpiredUnclaimedFileAndKeepsClaimedArtwork() {
        final CustomerUpload expired =
                spy(new CustomerUpload("owner-1", "file:customer-uploads/expired.webp", "image/webp", 12L));
        when(expired.getCreatedAt()).thenReturn(Instant.now().minus(Duration.ofDays(2)));
        final CustomerUpload claimed =
                spy(new CustomerUpload("owner-1", "file:customer-uploads/claimed.webp", "image/webp", 12L));
        claimed.setConsumedAt(Instant.now());
        when(customerUploadRepository.findByConsumedAtIsNullAndCreatedAtBefore(any()))
                .thenReturn(List.of(expired, claimed));
        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 5_000_000L);

        service.cleanupExpiredUploads();

        verify(storageService).deleteFile(URI.create(expired.getStorageUri()));
        verify(customerUploadRepository).delete(expired);
        verify(storageService, never()).deleteFile(URI.create(claimed.getStorageUri()));
        verify(customerUploadRepository, never()).delete(claimed);
    }

    private void verifyNoUploadInteractions() throws Exception {
        verify(imageConversionService, never()).convertToWebP(any());
        verify(storageService, never()).uploadFile(anyString(), any());
        verify(customerUploadRepository, never()).saveAndFlush(any());
    }
}
