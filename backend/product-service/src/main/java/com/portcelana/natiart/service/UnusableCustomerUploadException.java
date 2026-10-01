package com.portcelana.natiart.service;

/** Signals an unusable artwork reference while the order transaction is rejected. */
public final class UnusableCustomerUploadException extends IllegalArgumentException {
    private final String uploadId;

    public UnusableCustomerUploadException(String uploadId) {
        super("Custom artwork is no longer available");
        this.uploadId = uploadId;
    }

    public String getUploadId() {
        return uploadId;
    }
}
