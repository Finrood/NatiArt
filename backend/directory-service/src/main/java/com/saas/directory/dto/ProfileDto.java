package com.saas.directory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.saas.directory.model.Profile;

public class ProfileDto {
    private String id;
    private Long version;

    @NotBlank
    @Size(max = 100)
    private String firstname;

    @NotBlank
    @Size(max = 100)
    private String lastname;

    @NotBlank
    @Pattern(regexp = "(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})")
    private String cpf;

    @Pattern(regexp = "(?:|[0-9]{10,11}|\\([0-9]{2}\\) [0-9]{4,5}-[0-9]{4})")
    private String phone;

    @NotBlank
    @Size(max = 100)
    private String country;

    @NotBlank
    @Size(max = 2)
    @Pattern(regexp = "(?i)(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)")
    private String state;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 100)
    private String neighborhood;

    @NotBlank
    @Pattern(regexp = "(?:[0-9]{8}|[0-9]{5}-[0-9]{3})")
    private String zipCode;

    @NotBlank
    @Size(max = 255)
    private String street;

    @NotBlank
    @Size(max = 255)
    private String houseNumber;

    @Size(max = 255)
    private String complement;

    public static ProfileDto from(Profile profile) {
        if (profile == null) return null;
        return new ProfileDto()
                .setId(profile.getId())
                .setVersion(profile.getVersion())
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
                .setHouseNumber(profile.getHouseNumber())
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

    public Long getVersion() {
        return version;
    }

    public ProfileDto setVersion(Long version) {
        this.version = version;
        return this;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public ProfileDto setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
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
