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
                profileDto.getFirstname().trim(),
                profileDto.getLastname().trim(),
                profileDto.getCpf().replaceAll("[^0-9]", "").trim(),
                profileDto.getCountry().trim(),
                profileDto.getState().trim(),
                profileDto.getCity().trim(),
                profileDto.getNeighborhood().trim(),
                profileDto.getZipCode().trim(),
                profileDto.getStreet().trim(),
                user);
        if (profileDto.getPhone() != null) {
            profile.setPhone(profileDto.getPhone().trim());
        }
        if (profileDto.getComplement() != null) {
            profile.setComplement(profileDto.getComplement().trim());
        }

        return profileRepository.save(profile);
    }
}
