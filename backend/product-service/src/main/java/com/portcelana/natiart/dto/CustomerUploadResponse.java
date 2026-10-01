package com.portcelana.natiart.dto;

public class CustomerUploadResponse {
    private String uploadId;

    public CustomerUploadResponse() {}

    public CustomerUploadResponse(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public CustomerUploadResponse setUploadId(String uploadId) {
        this.uploadId = uploadId;
        return this;
    }
}
