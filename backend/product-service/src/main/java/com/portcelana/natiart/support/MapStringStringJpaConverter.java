package com.portcelana.natiart.support;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

public class MapStringStringJpaConverter extends JsonJpaConverter<Map<String, String>> {

    public MapStringStringJpaConverter() {
        super(new TypeReference<>() {});
    }

    @Override
    public Map<String, String> onNullValue() {
        return Collections.emptyMap();
    }
}
