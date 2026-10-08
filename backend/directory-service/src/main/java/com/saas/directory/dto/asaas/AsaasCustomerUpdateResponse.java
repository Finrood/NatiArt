package com.saas.directory.dto.asaas;

/** Only the provider identity and active state confirm an update. Missing fields fail closed. */
public record AsaasCustomerUpdateResponse(String id, Boolean deleted) {}
