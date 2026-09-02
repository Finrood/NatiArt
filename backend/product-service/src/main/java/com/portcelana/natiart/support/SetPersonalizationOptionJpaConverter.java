package com.portcelana.natiart.support;

import java.util.Collections;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;

import com.portcelana.natiart.model.support.PersonalizationOption;

public class SetPersonalizationOptionJpaConverter extends JsonJpaConverter<Set<PersonalizationOption>> {

    public SetPersonalizationOptionJpaConverter() {
        super(new TypeReference<>() {});
    }

    @Override
    public Set<PersonalizationOption> onNullValue() {
        return Collections.emptySet();
    }
}
