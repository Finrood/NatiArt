package com.saas.directory.service;

import com.saas.directory.dto.ProfileDto;
import com.saas.directory.model.Profile;
import com.saas.directory.model.User;
import com.saas.directory.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileManager {
    private final ProfileRepository profileRepository;

    public ProfileManager(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional
    public Profile createProfile(User user, ProfileDto profileDto) {
        final Profile profile = new Profile(
                profileDto.getFirstname(),
                profileDto.getLastname(),
                profileDto.getCpf().replaceAll("[^0-9]", ""),
                profileDto.getCountry(),
                profileDto.getState(),
                profileDto.getCity(),
                profileDto.getZipCode(),
                profileDto.getStreet(),
                user);
        profile.setPhone(profileDto.getPhone());
        profile.setComplement(profileDto.getComplement());
        return profileRepository.save(profile);
    }
}
