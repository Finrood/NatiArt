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
        final String cpf = required(profileDto.getCpf(), "Cpf", 14).replaceAll("[^0-9]", "");
        if (!isValidCpf(cpf)) {
            throw new IllegalArgumentException("Cpf is invalid");
        }
        final String zipCode = required(profileDto.getZipCode(), "Zip code", 9).replaceAll("[^0-9]", "");
        if (zipCode.length() != 8) {
            throw new IllegalArgumentException("Zip code is invalid");
        }
        final Profile profile = new Profile(
                required(profileDto.getFirstname(), "Firstname", 100),
                required(profileDto.getLastname(), "Lastname", 100),
                cpf,
                required(profileDto.getCountry(), "Country", 100),
                required(profileDto.getState(), "State", 100),
                required(profileDto.getCity(), "City", 100),
                required(profileDto.getNeighborhood(), "Neighborhood", 100),
                zipCode,
                required(profileDto.getStreet(), "Street", 255),
                user);
        if (profileDto.getPhone() != null) {
            final String phone = profileDto.getPhone().replaceAll("[^0-9]", "");
            if (!phone.isEmpty() && phone.length() != 10 && phone.length() != 11) {
                throw new IllegalArgumentException("Phone is invalid");
            }
            profile.setPhone(phone);
        }
        if (profileDto.getComplement() != null && !profileDto.getComplement().isBlank()) {
            profile.setComplement(required(profileDto.getComplement(), "Complement", 255));
        }

        return profileRepository.save(profile);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(String.format("%s cannot be empty", field));
        }
        final String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(String.format("%s is too long", field));
        }
        return trimmed;
    }

    private static boolean isValidCpf(String cpf) {
        if (cpf.length() != 11 || cpf.chars().distinct().count() == 1) {
            return false;
        }
        int firstSum = 0;
        for (int index = 0; index < 9; index++) {
            firstSum += Character.digit(cpf.charAt(index), 10) * (10 - index);
        }
        final int firstDigit = (firstSum * 10) % 11 % 10;
        if (firstDigit != Character.digit(cpf.charAt(9), 10)) {
            return false;
        }
        int secondSum = 0;
        for (int index = 0; index < 10; index++) {
            secondSum += Character.digit(cpf.charAt(index), 10) * (11 - index);
        }
        final int secondDigit = (secondSum * 10) % 11 % 10;
        return secondDigit == Character.digit(cpf.charAt(10), 10);
    }
}
