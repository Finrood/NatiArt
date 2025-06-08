package com.portcelana.natiart.service;

import com.luciad.imageio.webp.WebPWriteParam;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ImageConversionService {
    public List<MultipartFile> convertToWebP(List<MultipartFile> images) throws IOException {
        return images.parallelStream()
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
        final BufferedImage bufferedImage = ImageIO.read(image.getInputStream());

        if (bufferedImage == null) {
            throw new IOException("Failed to read image: " + image.getOriginalFilename());
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
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

        return new CustomMultipartFile(
                image.getName(),
                image.getOriginalFilename().replaceAll("\\.[^.]+$", ".webp"),
                "image/webp",
                baos.toByteArray()
        );
    }
}
