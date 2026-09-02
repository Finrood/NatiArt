package com.portcelana.natiart.support;

import java.util.Collections;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;

public class SetStringJpaConverter extends JsonJpaConverter<Set<String>> {

    public SetStringJpaConverter() {
        super(new TypeReference<>() {});
    }

    @Override
    public Set<String> onNullValue() {
        return Collections.emptySet();
    }
}
