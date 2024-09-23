package com.saas.directory.dto.asaas;

import com.saas.directory.dto.UserDto;

public class AsaasCustomerCreationRequest {
    private final String name;
    private final String cpfCnpj;
    private String email;
    private String phone;
    private String mobilePhone;
    private String address;
    private String addressNumber;
    private String complement;
    private String province;
    private String postalCode;
    private String externalReference;
    private boolean notificationDisabled;
    private String additionalEmails;
    private String municipalInscription;
    private String stateInscription;
    private String observations;
    private String groupName;
    private String company;

    public static AsaasCustomerCreationRequest from(UserDto userDto) {
        final String customerFullName = userDto.getProfile().getFirstname() + " " + userDto.getProfile().getLastname();
        return new AsaasCustomerCreationRequest(customerFullName, userDto.getProfile().getCpf())
                .setEmail(userDto.getUsername())
                .setPhone(userDto.getProfile().getPhone())
                .setPostalCode(userDto.getProfile().getZipCode())
                .setProvince(userDto.getProfile().getNeighborhood())
                .setComplement(userDto.getProfile().getComplement())
                .setAddress(userDto.getProfile().getStreet());
    }

    public AsaasCustomerCreationRequest(String name, String cpfCnpj) {
        this.name = name;
        this.cpfCnpj = cpfCnpj;
    }

    public String getName() {
        return name;
    }

    public String getCpfCnpj() {
        return cpfCnpj;
    }

    public String getEmail() {
        return email;
    }

    public AsaasCustomerCreationRequest setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public AsaasCustomerCreationRequest setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public AsaasCustomerCreationRequest setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public AsaasCustomerCreationRequest setAddress(String address) {
        this.address = address;
        return this;
    }

    public String getAddressNumber() {
        return addressNumber;
    }

    public AsaasCustomerCreationRequest setAddressNumber(String addressNumber) {
        this.addressNumber = addressNumber;
        return this;
    }

    public String getComplement() {
        return complement;
    }

    public AsaasCustomerCreationRequest setComplement(String complement) {
        this.complement = complement;
        return this;
    }

    public String getProvince() {
        return province;
    }

    public AsaasCustomerCreationRequest setProvince(String province) {
        this.province = province;
        return this;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public AsaasCustomerCreationRequest setPostalCode(String postalCode) {
        this.postalCode = postalCode;
        return this;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public AsaasCustomerCreationRequest setExternalReference(String externalReference) {
        this.externalReference = externalReference;
        return this;
    }

    public boolean isNotificationDisabled() {
        return notificationDisabled;
    }

    public AsaasCustomerCreationRequest setNotificationDisabled(boolean notificationDisabled) {
        this.notificationDisabled = notificationDisabled;
        return this;
    }

    public String getAdditionalEmails() {
        return additionalEmails;
    }

    public AsaasCustomerCreationRequest setAdditionalEmails(String additionalEmails) {
        this.additionalEmails = additionalEmails;
        return this;
    }

    public String getMunicipalInscription() {
        return municipalInscription;
    }

    public AsaasCustomerCreationRequest setMunicipalInscription(String municipalInscription) {
        this.municipalInscription = municipalInscription;
        return this;
    }

    public String getStateInscription() {
        return stateInscription;
    }

    public AsaasCustomerCreationRequest setStateInscription(String stateInscription) {
        this.stateInscription = stateInscription;
        return this;
    }

    public String getObservations() {
        return observations;
    }

    public AsaasCustomerCreationRequest setObservations(String observations) {
        this.observations = observations;
        return this;
    }

    public String getGroupName() {
        return groupName;
    }

    public AsaasCustomerCreationRequest setGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public String getCompany() {
        return company;
    }

    public AsaasCustomerCreationRequest setCompany(String company) {
        this.company = company;
        return this;
    }
}
