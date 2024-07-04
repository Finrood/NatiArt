package com.portcelana.natiart.support;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.Set;

public class SetStringJpaConverter extends JsonJpaConverter<Set<String>> {

    public SetStringJpaConverter() {
        super(new TypeReference<>() {});
    }

    @Override
    public Set<String> onNullValue() {
        return Collections.emptySet();
    }
}
