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
        private Profile profile;
        private String role;
        private String externalId;

        public String getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public Profile getProfile() {
            return profile;
        }

        public String getRole() {
            return role;
        }

        public String getExternalId() {
            return externalId;
        }
    }

    public static class Profile {
        private String id;
        private String firstname;
        private String lastname;
        private String cpf;
        private String phone;
        private String country;
        private String state;
        private String city;
        private String neighborhood;
        private String zipCode;
        private String street;
        private String complement;

        public String getId() {
            return id;
        }

        public String getFirstname() {
            return firstname;
        }

        public String getLastname() {
            return lastname;
        }

        public String getCpf() {
            return cpf;
        }

        public String getPhone() {
            return phone;
        }

        public String getCountry() {
            return country;
        }

        public String getState() {
            return state;
        }

        public String getCity() {
            return city;
        }

        public String getNeighborhood() {
            return neighborhood;
        }

        public String getZipCode() {
            return zipCode;
        }

        public String getStreet() {
            return street;
        }

        public String getComplement() {
            return complement;
        }
    }
}
