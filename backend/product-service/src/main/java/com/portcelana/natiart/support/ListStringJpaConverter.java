package com.portcelana.natiart.support;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

public class ListStringJpaConverter extends JsonJpaConverter<List<String>> {

    public ListStringJpaConverter() {
        super(new TypeReference<>() {});
    }

    @Override
    public List<String> onNullValue() {
        return Collections.emptyList();
    }
}
