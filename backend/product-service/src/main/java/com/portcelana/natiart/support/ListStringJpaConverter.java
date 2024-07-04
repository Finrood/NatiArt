package com.portcelana.natiart.support;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.List;

public class ListStringJpaConverter extends JsonJpaConverter<List<String>> {

    public ListStringJpaConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    public List<String> onNullValue() {
        return Collections.emptyList();
    }
}
