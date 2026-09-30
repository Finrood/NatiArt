package com.portcelana.natiart.dto.product;

public record PublicProductMetadataDto(String title, String description, String language, String type) {
    public static PublicProductMetadataDto from(String title, String description, String type) {
        return new PublicProductMetadataDto(escape(title), escape(description), "en", type);
    }

    // ASCII entities keep HTTP headers valid and untrusted catalog text out of markup.
    private static String escape(String value) {
        final StringBuilder escaped = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (codePoint >= 32
                    && codePoint <= 126
                    && codePoint != '&'
                    && codePoint != '<'
                    && codePoint != '>'
                    && codePoint != '\"'
                    && codePoint != '\'') {
                escaped.appendCodePoint(codePoint);
            } else {
                escaped.append("&#").append(codePoint).append(';');
            }
        });
        return escaped.toString();
    }
}
