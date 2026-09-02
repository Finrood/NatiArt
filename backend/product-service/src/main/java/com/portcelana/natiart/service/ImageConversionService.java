package com.portcelana.natiart.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.luciad.imageio.webp.WebPWriteParam;

@Service
public class ImageConversionService {
    static final int MAX_IMAGE_DIMENSION = 6000;
    static final long MAX_PIXELS = 24_000_000L;

    public List<MultipartFile> convertToWebP(List<MultipartFile> images) throws IOException {
        return images.stream()
                .map(image -> {
                    try {
                        return convertToWebP(image);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to convert image: " + image.getOriginalFilename(), e);
                    }
                })
                .toList();
    }

    private MultipartFile convertToWebP(MultipartFile image) throws IOException {
        validateDimensionsWithinLimit(image);

        final BufferedImage bufferedImage = ImageIO.read(image.getInputStream());

        if (bufferedImage == null) {
            throw new IOException("Failed to read image: " + image.getOriginalFilename());
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final ImageWriter writer =
                ImageIO.getImageWritersByMIMEType("image/webp").next();
        if (writer == null) {
            throw new IOException("No WebP ImageWriter found");
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);

            final WebPWriteParam param = new WebPWriteParam(writer.getLocale());
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType("Lossy");
            param.setCompressionQuality(0.6f);

            writer.write(null, new IIOImage(bufferedImage, null, null), param);
        } finally {
            writer.dispose();
        }

        final String originalName = image.getOriginalFilename();
        final String outputName = (originalName != null ? originalName : "image").replaceAll("\\.[^.]+$", ".webp");

        return new CustomMultipartFile(image.getName(), outputName, "image/webp", baos.toByteArray());
    }

    private void validateDimensionsWithinLimit(MultipartFile image) throws IOException {
        try (javax.imageio.stream.ImageInputStream stream = ImageIO.createImageInputStream(image.getInputStream())) {
            if (stream == null) {
                return;
            }
            final java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return;
            }
            final javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                final int width = reader.getWidth(0);
                final int height = reader.getHeight(0);
                if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION || (long) width * height > MAX_PIXELS) {
                    throw new IOException(String.format(
                            "Image dimensions %dx%d exceed the allowed limit of %dx%d pixels",
                            width, height, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION));
                }
            } finally {
                reader.dispose();
            }
        }
    }
}
