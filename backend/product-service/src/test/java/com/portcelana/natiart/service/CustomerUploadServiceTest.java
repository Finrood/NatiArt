package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        when(storageService.uploadFile(anyString(), any())).thenReturn(URI.create("file:///tmp/customer.webp"));
        when(customerUploadRepository.save(any(CustomerUpload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final CustomerUploadService service =
                new CustomerUploadService(customerUploadRepository, imageConversionService, storageService, 5_000_000L);

        final CustomerUploadResponse response = service.upload("owner-1", source);

        assertNotNull(response.getUploadId());
        final ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<CustomerUpload> upload = ArgumentCaptor.forClass(CustomerUpload.class);
        verify(storageService).uploadFile(key.capture(), any());
        verify(customerUploadRepository).save(upload.capture());
        assertEquals(response.getUploadId(), upload.getValue().getId());
        assertEquals("owner-1", upload.getValue().getOwnerExternalId());
        assertEquals("image/webp", upload.getValue().getContentType());
        assertEquals("customer-uploads/" + response.getUploadId() + ".webp", key.getValue());
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

    private void verifyNoUploadInteractions() throws Exception {
        verify(imageConversionService, never()).convertToWebP(any());
        verify(storageService, never()).uploadFile(anyString(), any());
        verify(customerUploadRepository, never()).save(any());
    }
}
