package com.saas.directory.dto;

public class UserAuthDto {
    private final String accessToken;
    private final String refreshToken;

    public UserAuthDto(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
