package com.saas.directory.dto.asaas;

import com.saas.directory.dto.ProfileDto;

public record AsaasCustomerUpdateRequest(
        String name,
        String cpfCnpj,
        String email,
        String phone,
        String address,
        String addressNumber,
        String complement,
        String province,
        String postalCode,
        String externalReference) {
    public static AsaasCustomerUpdateRequest from(String userId, String username, ProfileDto profile) {
        return new AsaasCustomerUpdateRequest(
                profile.getFirstname() + " " + profile.getLastname(),
                profile.getCpf(),
                username,
                profile.getPhone() == null ? "" : profile.getPhone(),
                profile.getStreet(),
                profile.getHouseNumber(),
                profile.getComplement() == null ? "" : profile.getComplement(),
                profile.getNeighborhood(),
                profile.getZipCode(),
                userId);
    }

    @Override
    public String toString() {
        return "AsaasCustomerUpdateRequest[redacted]";
    }
}
