package com.portcelana.natiart.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.portcelana.natiart.dto.ProductDto;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.service.ImageConversionService;
import com.portcelana.natiart.service.ProductManager;
import com.portcelana.natiart.storage.InputFile;

class ProductImageManifestHttpTest {
    @Test
    void multipartManifestUploadIdSurvivesRealImageConversion() throws Exception {
        final ProductManager manager = mock(ProductManager.class);
        when(manager.updateProduct(any(ProductDto.class), anyList())).thenReturn(new Product("Art", BigDecimal.TEN));
        final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ProductController(manager, new ImageConversionService()))
                .build();
        final String uploadId = UUID.randomUUID().toString();
        final String json = "{\"id\":\"p1\",\"label\":\"Art\",\"originalPrice\":10,\"imageManifest\":[{\"uploadId\":\""
                + uploadId + "\"}]}";
        final ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", png);
        mvc.perform(multipart("/products/p1")
                        .file(new MockMultipartFile(
                                "productDto", "", "application/json", json.getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("newImages", uploadId + ".webp", "image/png", png.toByteArray()))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk());
        final ArgumentCaptor<ProductDto> dto = ArgumentCaptor.forClass(ProductDto.class);
        final ArgumentCaptor<List<InputFile>> inputs = ArgumentCaptor.captor();
        verify(manager).updateProduct(dto.capture(), inputs.capture());
        assertEquals(uploadId, dto.getValue().getImageManifest().getFirst().uploadId());
        assertEquals(uploadId + ".webp", inputs.getValue().getFirst().filename());
        assertEquals("image/webp", inputs.getValue().getFirst().contentType());
        inputs.getValue().getFirst().inputStream().close();
    }
}
