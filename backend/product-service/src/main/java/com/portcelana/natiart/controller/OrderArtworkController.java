package com.portcelana.natiart.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.portcelana.natiart.service.OrderArtworkService;

@RestController
public class OrderArtworkController {
    private final OrderArtworkService artwork;

    public OrderArtworkController(OrderArtworkService artwork) {
        this.artwork = artwork;
    }

    @GetMapping("/admin/orders/{orderId}/items/{itemId}/artwork")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> getArtwork(@PathVariable String orderId, @PathVariable String itemId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/webp"))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"order-artwork.webp\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(artwork.openArtworkOrDie(orderId, itemId)));
    }
}
