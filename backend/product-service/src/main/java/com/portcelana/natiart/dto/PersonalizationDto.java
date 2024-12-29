package com.portcelana.natiart.dto;

import com.portcelana.natiart.model.Personalization;
import com.portcelana.natiart.model.support.PersonalizationOption;

import java.util.HashMap;
import java.util.Map;

public class PersonalizationDto {
    private String id;
    private Map<PersonalizationOption, String> personalizationOptions = new HashMap<>();

    public static PersonalizationDto from(Personalization personalization) {
        return new PersonalizationDto()
                .setId(personalization.getId())
                .setPersonalizationOptions(personalization.getPersonalizationOptions());
    }

    public String getId() {
        return id;
    }

    public PersonalizationDto setId(String id) {
        this.id = id;
        return this;
    }

    public Map<PersonalizationOption, String> getPersonalizationOptions() {
        return personalizationOptions;
    }

    public PersonalizationDto setPersonalizationOptions(Map<PersonalizationOption, String> personalizationOptions) {
        this.personalizationOptions = personalizationOptions;
        return this;
    }
}
