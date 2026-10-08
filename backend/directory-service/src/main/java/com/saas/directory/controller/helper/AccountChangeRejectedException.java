package com.saas.directory.controller.helper;

public class AccountChangeRejectedException extends RuntimeException {
    public enum Reason {
        CURRENT_PASSWORD_INCORRECT,
        TRY_LATER,
        PROFILE_CONFLICT,
        ACCOUNT_SETUP_IN_PROGRESS,
        PROVIDER_UNAVAILABLE
    }

    private final Reason reason;

    public AccountChangeRejectedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
