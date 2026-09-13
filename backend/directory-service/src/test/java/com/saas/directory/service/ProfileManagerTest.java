package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.saas.directory.dto.ProfileDto;
import com.saas.directory.model.Profile;
import com.saas.directory.model.User;
import com.saas.directory.repository.ProfileRepository;

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
                .setCpf("12345678909")
                .setPhone("11987654321")
                .setCountry("USA")
                .setState("California")
                .setCity("Los Angeles")
                .setNeighborhood("Campinas")
                .setZipCode("12345678")
                .setStreet("Main Street")
                .setComplement("Apartment 123");

        // Mock the repository behavior
        final Profile expectedProfile = new Profile(
                "John",
                "Doe",
                "12345678909",
                "USA",
                "California",
                "Los Angeles",
                "Campinas",
                "12345678",
                "Main Street",
                user);
        when(profileRepository.save(any(Profile.class))).thenReturn(expectedProfile);

        // Act
        Profile createdProfile = profileManager.createProfile(user, profileDto);

        // Assert
        assertEquals(expectedProfile, createdProfile);
    }

    @Test
    public void test_createProfile_trims_and_normalizes_required_fields() {
        final User user = new User("username", "password");
        final ProfileDto profileDto = new ProfileDto()
                .setFirstname("  John  ")
                .setLastname("Doe")
                .setCpf("123.456.789-09")
                .setCountry("USA")
                .setState("California")
                .setCity("Los Angeles")
                .setNeighborhood("Campinas")
                .setZipCode("12345678")
                .setStreet("Main Street");
        when(profileRepository.save(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Profile createdProfile = profileManager.createProfile(user, profileDto);

        assertEquals("John", createdProfile.getFirstname());
        assertEquals("12345678909", createdProfile.getCpf());
    }

    @Test
    public void test_createProfile_null_profile_throws_illegal_argument() {
        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> profileManager.createProfile(new User("username", "p"), null));

        assertEquals("Profile cannot be null", exception.getMessage());
    }

    @Test
    public void test_createProfile_blank_required_field_throws_illegal_argument() {
        final ProfileDto profileDto = new ProfileDto()
                .setFirstname("   ")
                .setLastname("Doe")
                .setCpf("12345678909")
                .setCountry("USA")
                .setState("California")
                .setCity("Los Angeles")
                .setNeighborhood("Campinas")
                .setZipCode("12345678")
                .setStreet("Main Street");

        final IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> profileManager.createProfile(new User("username", "p"), profileDto));

        assertEquals("Firstname cannot be empty", exception.getMessage());
    }
}
