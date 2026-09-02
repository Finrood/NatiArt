package com.portcelana.natiart.service;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ImageConversionServiceTest {

    private final ImageConversionService service = new ImageConversionService();

    private MultipartFile pngOf(int width, int height) throws IOException {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("newImages", "test.png", "image/png", out.toByteArray());
    }

    @Test
    void convertsNormalImageToWebP() throws IOException {
        var results = service.convertToWebP(java.util.List.of(pngOf(200, 150)));

        assertEquals(1, results.size());
        assertEquals("image/webp", results.get(0).getContentType());
        assertEquals("test.webp", results.get(0).getOriginalFilename());
        assertTrue(results.get(0).getSize() > 0);
    }

    @Test
    void rejectsOversizedImageBeforeDecoding() throws IOException {
        final MultipartFile huge = pngOf(7000, 10);

        assertThrows(RuntimeException.class, () -> service.convertToWebP(java.util.List.of(huge)));
    }

    @Test
    void rejectsExcessivePixelCountEvenWithBalancedSides() throws IOException {
        final MultipartFile dense = pngOf(
                ImageConversionService.MAX_IMAGE_DIMENSION,
                (int) (ImageConversionService.MAX_PIXELS / ImageConversionService.MAX_IMAGE_DIMENSION) + 1);

        assertThrows(RuntimeException.class, () -> service.convertToWebP(java.util.List.of(dense)));
    }
}
