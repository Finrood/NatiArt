package com.portcelana.natiart.service;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.portcelana.natiart.dto.PersonalizationDto;
import com.portcelana.natiart.model.Product;
import com.portcelana.natiart.model.support.PersonalizationOption;

/** Keeps quote and order fulfillment identities and option checks identical. */
final class PersonalizationRules {
    private static final int MAX_VALUE_LENGTH = 128;

    private PersonalizationRules() {}

    static Map<PersonalizationOption, String> validatedOptions(PersonalizationDto dto, Product product) {
        if (dto == null) {
            return Map.of();
        }
        final Map<PersonalizationOption, String> requested = dto.getPersonalizationOptions();
        if (requested == null) {
            throw new IllegalArgumentException("Personalization options must be an object");
        }
        final Map<PersonalizationOption, String> accepted = new EnumMap<>(PersonalizationOption.class);
        for (Map.Entry<PersonalizationOption, String> option : requested.entrySet()) {
            final PersonalizationOption kind = option.getKey();
            final String value = option.getValue();
            if (kind == null || value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("Personalization options are invalid");
            }
            if (product.getAvailablePersonalizations() == null
                    || !product.getAvailablePersonalizations().contains(kind)) {
                throw new IllegalArgumentException("The requested personalization is not available for this product");
            }
            switch (kind) {
                case GOLDEN_BORDER -> {
                    if (!"true".equals(value)) {
                        throw new IllegalArgumentException("Golden border personalization must be true");
                    }
                }
                case CUSTOM_IMAGE -> {
                    try {
                        UUID.fromString(value);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Custom artwork upload is invalid");
                    }
                }
            }
            accepted.put(kind, value);
        }
        return accepted;
    }

    static String canonical(Map<PersonalizationOption, String> options) {
        return options.entrySet().stream()
                .sorted((left, right) ->
                        left.getKey().name().compareTo(right.getKey().name()))
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining("|"));
    }
}
