package com.portcelana.natiart.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.BatchSize;

import com.portcelana.natiart.model.support.PersonalizationOption;

@Entity
public class Personalization {
    @Id
    private final String id;

    @ElementCollection
    @BatchSize(size = 50)
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "personalizationOptions")
    private final Map<PersonalizationOption, String> personalizationOptions = new HashMap<>();

    /** Server-resolved artwork retained for fulfilment; never populated from a client URI. */
    @OneToOne(fetch = FetchType.EAGER)
    private CustomerUpload customImageUpload;

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

    public CustomerUpload getCustomImageUpload() {
        return customImageUpload;
    }

    public Personalization setCustomImageUpload(CustomerUpload customImageUpload) {
        this.customImageUpload = customImageUpload;
        return this;
    }
}
