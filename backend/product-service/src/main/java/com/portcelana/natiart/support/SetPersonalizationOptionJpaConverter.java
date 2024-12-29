package com.portcelana.natiart.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.portcelana.natiart.model.support.PersonalizationOption;

import java.util.Collections;
import java.util.Set;

public class SetPersonalizationOptionJpaConverter extends JsonJpaConverter<Set<PersonalizationOption>> {

    public SetPersonalizationOptionJpaConverter() {
        super(new TypeReference<>() {
        });
    }

    @Override
    public Set<PersonalizationOption> onNullValue() {
        return Collections.emptySet();
    }
}
