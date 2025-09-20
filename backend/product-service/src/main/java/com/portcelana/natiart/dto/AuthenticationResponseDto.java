package com.portcelana.natiart.dto;

import java.util.Set;

public class AuthenticationResponseDto {
    private Set<Authority> authorities;
    private Object details;
    private boolean authenticated;
    private Principal principal;
    private Object credentials;
    private String name;

    public Set<Authority> getAuthorities() {
        return authorities;
    }

    public Object getDetails() {
        return details;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public Principal getPrincipal() {
        return principal;
    }

    public Object getCredentials() {
        return credentials;
    }

    public String getName() {
        return name;
    }

    public static class Authority {
        private String authority;

        public String getAuthority() {
            return authority;
        }
    }

    public static class Principal {
        private String id;
        private String username;
        private String profile;
        private String role;
        private String externalId;

        public String getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getProfile() {
            return profile;
        }

        public String getRole() {
            return role;
        }

        public String getExternalId() {
            return externalId;
        }
    }
}
