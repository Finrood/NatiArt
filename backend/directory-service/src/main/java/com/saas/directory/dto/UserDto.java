package com.saas.directory.dto;

import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;

public class UserDto {
    private String id;
    private String username;
    private ProfileDto profile;
    private RoleName role;
    private String externalId;

    public static UserDto from(User user) {
        if (user == null) return null;
        return new UserDto()
                .setId(user.getId())
                .setUsername(user.getUsername())
                .setProfile(ProfileDto.from(user.getProfile()))
                .setRole(user.getRole().getLabel());
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

    public ProfileDto getProfile() {
        return profile;
    }

    public UserDto setProfile(ProfileDto profile) {
        this.profile = profile;
        return this;
    }

    public RoleName getRole() {
        return role;
    }

    public UserDto setRole(RoleName role) {
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
