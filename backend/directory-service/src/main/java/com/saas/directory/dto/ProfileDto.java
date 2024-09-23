package com.saas.directory.dto;

import com.saas.directory.model.Profile;

public class ProfileDto {
    private String id;
    private String firstname;
    private String lastname;
    private String cpf;
    private String phone;
    private String country;
    private String state;
    private String city;
    private String neighborhood;
    private String zipCode;
    private String street;
    private String complement;

    public static ProfileDto from(Profile profile) {
        if (profile == null) return null;
        return new ProfileDto()
                .setId(profile.getId())
                .setFirstname(profile.getFirstname())
                .setLastname(profile.getLastname())
                .setCpf(profile.getCpf())
                .setPhone(profile.getPhone())
                .setCountry(profile.getCountry())
                .setState(profile.getState())
                .setCity(profile.getCity())
                .setNeighborhood(profile.getNeighborhood())
                .setZipCode(profile.getZipCode())
                .setStreet(profile.getStreet())
                .setComplement(profile.getComplement());
    }

    public String getId() {
        return id;
    }

    public ProfileDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getFirstname() {
        return firstname;
    }

    public ProfileDto setFirstname(String firstname) {
        this.firstname = firstname;
        return this;
    }

    public String getLastname() {
        return lastname;
    }

    public ProfileDto setLastname(String lastname) {
        this.lastname = lastname;
        return this;
    }

    public String getCpf() {
        return cpf;
    }

    public ProfileDto setCpf(String cpf) {
        this.cpf = cpf;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public ProfileDto setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getCountry() {
        return country;
    }

    public ProfileDto setCountry(String country) {
        this.country = country;
        return this;
    }

    public String getState() {
        return state;
    }

    public ProfileDto setState(String state) {
        this.state = state;
        return this;
    }

    public String getCity() {
        return city;
    }

    public ProfileDto setCity(String city) {
        this.city = city;
        return this;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public ProfileDto setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
        return this;
    }

    public String getZipCode() {
        return zipCode;
    }

    public ProfileDto setZipCode(String zipCode) {
        this.zipCode = zipCode;
        return this;
    }

    public String getStreet() {
        return street;
    }

    public ProfileDto setStreet(String street) {
        this.street = street;
        return this;
    }

    public String getComplement() {
        return complement;
    }

    public ProfileDto setComplement(String complement) {
        this.complement = complement;
        return this;
    }
}
