package com.saas.directory.dto;

import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;

public record AccountStateDto(String id, String username, boolean active, RoleName role) {
    public static AccountStateDto from(User user) {
        return new AccountStateDto(
                user.getId(),
                user.getUsername(),
                user.isActive(),
                user.getRole().getLabel());
    }
}
