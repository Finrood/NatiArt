package com.portcelana.natiart.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;

/**
 * Pins the log-hygiene contract on the hot read paths: catalog and image reads
 * must never emit INFO-or-higher events (client-controlled input must not be
 * echoed into logs at that level), so normal browsing traffic cannot drown the
 * mutating/admin signals.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerLoggingTest {

    @Mock
    private ProductManager productManager;

    @Mock
    private ImageConversionService imageConversionService;

    @InjectMocks
    private ProductController productController;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        listAppender = new ListAppender<>();
        listAppender.start();
        ((Logger) LoggerFactory.getLogger(ProductController.class)).addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(ProductController.class)).detachAppender(listAppender);
    }

    @Test
    void catalogReads_emitNoInfoOrHigherEvents() {
        when(productManager.getProducts(any())).thenReturn(List.of());
        when(productManager.getNewProducts(any())).thenReturn(List.of());
        when(productManager.getFeaturedProducts(any())).thenReturn(List.of());

        productController.getProducts(0, 20);
        productController.getNewProducts(0, 20);
        productController.getFeaturedProducts(0, 20);

        assertTrue(listAppender.list.stream()
                .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.INFO)));
    }

    @Test
    void imageRead_doesNotEchoClientControlledPathAtInfoOrHigher() throws IOException {
        when(productManager.getProductImage(anyString())).thenReturn(mock(InputStreamResource.class));

        productController.getProductImage("../../secret");

        assertTrue(listAppender.list.stream()
                .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.INFO)));
    }
}
