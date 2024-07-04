package com.portcelana.natiart.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Optional;

public class JsonJpaConverter<T> implements AttributeConverter<T, String> {

    // Using custom object mapper because we don't want it to be the exact same serialized as
    // for the GUI + it makes the module self-contained
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

    private final Class<T> clazz;
    private final TypeReference<T> typeReference;

    public JsonJpaConverter(Class<T> clazz) {
        Assert.isTrue(clazz != null, "Class cannot be null");
        this.clazz = clazz;
        this.typeReference = null;
    }

    public JsonJpaConverter(TypeReference<T> typeReference) {
        Assert.isTrue(typeReference != null, "Type reference cannot be null");
        this.clazz = null;
        this.typeReference = typeReference;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        return writeValue(attribute);
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        return Optional.ofNullable(dbData)
                .map(this::readValue)
                .orElseGet(this::onNullValue);
    }

    public T onNullValue() {
        return null;
    }

    // ----------------------------------------------------------------------

    private String writeValue(T attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to convert map to String [%s]", e);
        }
    }

    private T readValue(String dbData) {
        try {
            if (clazz != null) {
                return OBJECT_MAPPER.readValue(dbData, clazz);
            } else {
                return OBJECT_MAPPER.readValue(dbData, typeReference);
            }
        } catch (IOException e) {
            Object targetClazz = clazz == null ? typeReference : clazz;
            throw new IllegalStateException(String.format("Unable to convert String to %s [%s]", targetClazz, dbData), e);
        }
    }
}
