package com.saas.directory.service;

import com.saas.directory.dto.ProfileDto;
import com.saas.directory.model.Profile;
import com.saas.directory.model.User;
import com.saas.directory.repository.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProfileManagerTest {
    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileManager profileManager;

    @Test
    public void test_createProfile_returns_created_profile() {
        // Arrange
        User user = new User("username", "password");
        ProfileDto profileDto = new ProfileDto()
                .setFirstname("John")
                .setLastname("Doe")
                .setPhone("123456789")
                .setCountry("USA")
                .setState("California")
                .setCity("Los Angeles")
                .setZipCode("12345")
                .setStreet("Main Street")
                .setComplement("Apartment 123");

        // Mock the repository behavior
        final Profile expectedProfile = new Profile("John", "Doe", "00000000011", "USA", "California", "Los Angeles", "12345", "Main Street", user);
        when(profileRepository.save(any(Profile.class))).thenReturn(expectedProfile);

        // Act
        Profile createdProfile = profileManager.createProfile(user, profileDto);

        // Assert
        assertEquals(expectedProfile, createdProfile);
    }
}