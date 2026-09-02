package com.portcelana.natiart.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.*;

import com.portcelana.natiart.model.support.PersonalizationOption;

@Entity
public class Personalization {
    @Id
    private final String id;

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "personalizationOptions")
    private final Map<PersonalizationOption, String> personalizationOptions = new HashMap<>();

    public Personalization() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public Map<PersonalizationOption, String> getPersonalizationOptions() {
        return personalizationOptions;
    }

    public Personalization setPersonalizationOptions(Map<PersonalizationOption, String> personalizationOptions) {
        this.personalizationOptions.clear();
        this.personalizationOptions.putAll(personalizationOptions);
        return this;
    }
}
