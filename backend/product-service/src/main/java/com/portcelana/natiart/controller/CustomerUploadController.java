package com.portcelana.natiart.controller;

import java.io.IOException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.portcelana.natiart.dto.AuthenticationResponseDto;
import com.portcelana.natiart.dto.CustomerUploadResponse;
import com.portcelana.natiart.service.CustomerUploadService;

@RestController
public class CustomerUploadController {
    private final CustomerUploadService customerUploadService;

    public CustomerUploadController(CustomerUploadService customerUploadService) {
        this.customerUploadService = customerUploadService;
    }

    @PostMapping(value = "/customer/uploads", consumes = "multipart/form-data")
    @PreAuthorize("isFullyAuthenticated()")
    public CustomerUploadResponse uploadArtwork(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticationResponseDto.Principal principal)
            throws IOException {
        return customerUploadService.upload(principal != null ? principal.getExternalId() : null, file);
    }
}
