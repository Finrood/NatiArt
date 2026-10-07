package com.portcelana.natiart.service;

import java.io.InputStream;
import java.net.URI;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.portcelana.natiart.controller.helper.ResourceNotFoundException;
import com.portcelana.natiart.model.CustomerOrder;
import com.portcelana.natiart.model.CustomerOrderItem;
import com.portcelana.natiart.model.CustomerUpload;
import com.portcelana.natiart.model.support.PersonalizationOption;
import com.portcelana.natiart.repository.CustomerUploadRepository;
import com.portcelana.natiart.storage.StorageService;

/** Reads only artwork claimed by a line in the selected fulfillment order. */
@Service
public class OrderArtworkService {
    private final OrderManager orderManager;
    private final CustomerUploadRepository uploads;
    private final StorageService storage;

    public OrderArtworkService(OrderManager orderManager, CustomerUploadRepository uploads, StorageService storage) {
        this.orderManager = orderManager;
        this.uploads = uploads;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public InputStream openArtworkOrDie(String orderId, String itemId) {
        final CustomerOrder order = orderManager.getOrderById(orderId);
        final CustomerOrderItem item = order.getItems().stream()
                .filter(line -> line.getId().equals(itemId))
                .findFirst()
                .orElseThrow(OrderArtworkService::notFound);
        if (item.getPersonalization() == null) throw notFound();
        final String uploadId =
                item.getPersonalization().getPersonalizationOptions().get(PersonalizationOption.CUSTOM_IMAGE);
        if (uploadId == null) throw notFound();
        final CustomerUpload upload = uploads.findById(uploadId).orElseThrow(OrderArtworkService::notFound);
        if (upload.getReadyAt() == null
                || upload.getConsumedAt() == null
                || !Objects.equals(order.getOwnerExternalId(), upload.getOwnerExternalId())) throw notFound();
        return storage.openFile(URI.create(upload.getStorageUri()));
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Order artwork was not found");
    }
}
