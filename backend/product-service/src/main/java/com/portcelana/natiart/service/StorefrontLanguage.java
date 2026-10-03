package com.portcelana.natiart.service;

public enum StorefrontLanguage {
    ENGLISH("en", "Handmade Art"),
    PORTUGUESE("pt-BR", "Arte artesanal");

    private final String code;
    private final String shopTitle;

    StorefrontLanguage(String code, String shopTitle) {
        this.code = code;
        this.shopTitle = shopTitle;
    }

    public String code() {
        return code;
    }

    public String shopTitle() {
        return shopTitle;
    }

    public String shopDescription(String name) {
        return this == PORTUGUESE ? "Conhe\u00e7a os produtos da " + name + "." : "Browse products from " + name + ".";
    }

    public static StorefrontLanguage fromCode(String code) {
        return switch (code) {
            case "en" -> ENGLISH;
            case "pt-BR" -> PORTUGUESE;
            default -> throw new IllegalArgumentException("Unsupported storefront language");
        };
    }
}
