package com.portcelana.natiart.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import com.portcelana.natiart.controller.CustomerUploadController;
import com.portcelana.natiart.dto.AuthenticationResponseDto;

class CustomerUploadControllerSecurityTest {

    @Test
    void artworkUploadRequiresFullAuthentication() throws Exception {
        final Method uploadArtwork = CustomerUploadController.class.getMethod(
                "uploadArtwork", MultipartFile.class, AuthenticationResponseDto.Principal.class);
        final PreAuthorize preAuthorize = uploadArtwork.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("isFullyAuthenticated()", preAuthorize.value());
    }
}
