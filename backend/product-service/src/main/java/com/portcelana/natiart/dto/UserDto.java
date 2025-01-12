package com.portcelana.natiart.dto;

public class UserDto {
    private String id;
    private String username;
    private String role;
    private String externalId;

    public UserDto() {
    }

    public String getId() {
        return id;
    }

    public UserDto setId(String id) {
        this.id = id;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public UserDto setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getRole() {
        return role;
    }

    public UserDto setRole(String role) {
        this.role = role;
        return this;
    }

    public String getExternalId() {
        return externalId;
    }

    public UserDto setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }
}
