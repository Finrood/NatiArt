package com.saas.directory.dto.asaas;

public class AsaasCustomerCreationResponse {
    private final String object;
    private final String id;
    private final String dateCreated;
    private final String name;
    private final String email;
    private final String company;
    private final String phone;
    private final String mobilePhone;
    private final String address;
    private final String addressNumber;
    private final String complement;
    private final String province;
    private final String postalCode;
    private final String cpfCnpj;
    private final String personType;
    private final boolean deleted;
    private final String additionalEmails;
    private final String externalReference;
    private final boolean notificationDisabled;
    private final String observations;
    private final String municipalInscription;
    private final String stateInscription;
    private final boolean canDelete;
    private final String cannotBeDeletedReason;
    private final boolean canEdit;
    private final String cannotEditReason;
    private final int city;
    private final String cityName;
    private final String state;
    private final String country;

    public AsaasCustomerCreationResponse(String object, String id, String dateCreated, String name, String email, String company, String phone, String mobilePhone, String address, String addressNumber, String complement, String province, String postalCode, String cpfCnpj, String personType, boolean deleted, String additionalEmails, String externalReference, boolean notificationDisabled, String observations, String municipalInscription, String stateInscription, boolean canDelete, String cannotBeDeletedReason, boolean canEdit, String cannotEditReason, int city, String cityName, String state, String country) {
        this.object = object;
        this.id = id;
        this.dateCreated = dateCreated;
        this.name = name;
        this.email = email;
        this.company = company;
        this.phone = phone;
        this.mobilePhone = mobilePhone;
        this.address = address;
        this.addressNumber = addressNumber;
        this.complement = complement;
        this.province = province;
        this.postalCode = postalCode;
        this.cpfCnpj = cpfCnpj;
        this.personType = personType;
        this.deleted = deleted;
        this.additionalEmails = additionalEmails;
        this.externalReference = externalReference;
        this.notificationDisabled = notificationDisabled;
        this.observations = observations;
        this.municipalInscription = municipalInscription;
        this.stateInscription = stateInscription;
        this.canDelete = canDelete;
        this.cannotBeDeletedReason = cannotBeDeletedReason;
        this.canEdit = canEdit;
        this.cannotEditReason = cannotEditReason;
        this.city = city;
        this.cityName = cityName;
        this.state = state;
        this.country = country;
    }

    public String getObject() {
        return object;
    }

    public String getId() {
        return id;
    }

    public String getDateCreated() {
        return dateCreated;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getCompany() {
        return company;
    }

    public String getPhone() {
        return phone;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressNumber() {
        return addressNumber;
    }

    public String getComplement() {
        return complement;
    }

    public String getProvince() {
        return province;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCpfCnpj() {
        return cpfCnpj;
    }

    public String getPersonType() {
        return personType;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getAdditionalEmails() {
        return additionalEmails;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public boolean isNotificationDisabled() {
        return notificationDisabled;
    }

    public String getObservations() {
        return observations;
    }

    public String getMunicipalInscription() {
        return municipalInscription;
    }

    public String getStateInscription() {
        return stateInscription;
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public String getCannotBeDeletedReason() {
        return cannotBeDeletedReason;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public String getCannotEditReason() {
        return cannotEditReason;
    }

    public int getCity() {
        return city;
    }

    public String getCityName() {
        return cityName;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }
}
