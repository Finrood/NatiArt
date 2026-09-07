package com.saas.directory.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saas.directory.dto.ProfileDto;
import com.saas.directory.model.Profile;
import com.saas.directory.model.User;
import com.saas.directory.repository.ProfileRepository;

@Service
public class ProfileManager {
    private final ProfileRepository profileRepository;

    public ProfileManager(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public Profile createProfile(User user, ProfileDto profileDto) {
        if (profileDto == null) {
            throw new IllegalArgumentException("Profile cannot be null");
        }
        final Profile profile = new Profile(
                required(profileDto.getFirstname(), "Firstname"),
                required(profileDto.getLastname(), "Lastname"),
                required(profileDto.getCpf(), "Cpf").replaceAll("[^0-9]", ""),
                required(profileDto.getCountry(), "Country"),
                required(profileDto.getState(), "State"),
                required(profileDto.getCity(), "City"),
                required(profileDto.getNeighborhood(), "Neighborhood"),
                required(profileDto.getZipCode(), "Zip code"),
                required(profileDto.getStreet(), "Street"),
                user);
        if (profileDto.getPhone() != null) {
            profile.setPhone(profileDto.getPhone().trim());
        }
        if (profileDto.getComplement() != null) {
            profile.setComplement(profileDto.getComplement().trim());
        }

        return profileRepository.save(profile);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(String.format("%s cannot be empty", field));
        }
        return value.trim();
    }
}
