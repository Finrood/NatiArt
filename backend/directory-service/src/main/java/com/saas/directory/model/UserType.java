package com.saas.directory.model;

public enum UserType {
    ACTIVE,
    INACTIVE,
    DELETED,
    // A ghost user is a user that did not explicitly create its account.
    // The account could have been created by SSO, or a guest user making an order
    // Ghost users have a random password set and should never be able to log in manually
    GHOST
}
