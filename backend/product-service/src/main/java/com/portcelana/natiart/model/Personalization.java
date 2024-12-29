package com.portcelana.natiart.model;

import com.portcelana.natiart.model.support.PersonalizationOption;
import jakarta.persistence.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
