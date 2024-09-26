package com.saas.directory.service;

import com.saas.directory.controller.helper.ResourceAlreadyExistsException;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.ProfileDto;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.model.Profile;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.management.relation.RoleNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserManagerTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExternalUserRepository externalUserRepository = mock(ExternalUserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ProfileManager profileManager = mock(ProfileManager.class);

    private UserManager userManager;

    @BeforeEach
    public void initContext() {
        userManager = new UserManager(
                userRepository,
                externalUserRepository,
                roleRepository,
                profileManager,
                passwordEncoder
        );
    }

    @Test
    public void test_create_new_user_with_unique_username_and_password() throws RoleNotFoundException {
        // Prepare test data
        final ProfileDto profileDto = new ProfileDto();
        profileDto.setFirstname("John");
        profileDto.setLastname("Doe");
        profileDto.setPhone("+1234567890");
        profileDto.setCountry("USA");
        profileDto.setState("California");
        profileDto.setCity("Los Angeles");
        profileDto.setZipCode("90001");
        profileDto.setStreet("123 Main St");
        profileDto.setComplement("Apt 101");

        final UserRegistrationDto userRegistrationDto = new UserRegistrationDto("new_username", "password", profileDto);

        final User user = new User("new_username", "password");
        when(userRepository.existsUserByUsernameIgnoreCase("new_username")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);
        final Profile profile = new Profile("John", "Doe", "00000000011", "USA", "USA", "Los Angeles", "90001", "123 Main St", user)
                .setPhone("+1234567890")
                .setComplement("Apt 101");
        when(profileManager.createProfile(any(User.class), any(ProfileDto.class))).thenReturn(profile);
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));

        // Perform the registration
        final User result = userManager.registerUser(userRegistrationDto);

        assertEquals(user, result);
    }

    @Test
    public void test_retrieve_existing_user_by_username() {
        // Prepare test data
        final User user = new User("existing_username", "password");
        when(userRepository.findUserByUsernameIgnoreCase("existing_username")).thenReturn(Optional.of(user));

        // Perform the retrieval
        final Optional<User> result = userManager.getUser("existing_username");

        assertEquals(Optional.of(user), result);
    }

    @Test
    public void test_check_user_exists_by_username() {
        // Prepare test data
        when(userRepository.existsUserByUsernameIgnoreCase("existing_username")).thenReturn(true);

        // Perform the check
        final boolean result = userManager.userExist("existing_username");

        assertTrue(result);
    }

    @Test
    public void test_register_new_user_with_unique_username_and_password() throws RoleNotFoundException {
        // Prepare test data
        final ProfileDto profileDto = new ProfileDto();
        profileDto.setFirstname("John");
        profileDto.setLastname("Doe");
        profileDto.setPhone("+1234567890");
        profileDto.setCountry("USA");
        profileDto.setState("California");
        profileDto.setCity("Los Angeles");
        profileDto.setZipCode("90001");
        profileDto.setStreet("123 Main St");
        profileDto.setComplement("Apt 101");

        final UserRegistrationDto userRegistrationDto = new UserRegistrationDto("new_username", "password", profileDto);

        final User user = new User("new_username", "password");
        when(userRepository.existsUserByUsernameIgnoreCase("new_username")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);
        final Profile profile = new Profile("John", "Doe", "00000000011", "USA", "USA", "Los Angeles", "90001", "123 Main St", user)
                .setPhone("+1234567890")
                .setComplement("Apt 101");
        when(profileManager.createProfile(any(User.class), any(ProfileDto.class))).thenReturn(profile);
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));

        // Perform the registration
        final User result = userManager.registerUser(userRegistrationDto);

        assertEquals(user, result);
    }

    @Test
    public void testRegisterUser_DuplicateUsername() {
        // Prepare test data
        final ProfileDto profileDto = new ProfileDto();
        profileDto.setFirstname("John");
        profileDto.setLastname("Doe");
        profileDto.setPhone("+1234567890");
        profileDto.setCountry("USA");
        profileDto.setState("California");
        profileDto.setCity("Los Angeles");
        profileDto.setZipCode("90001");
        profileDto.setStreet("123 Main St");
        profileDto.setComplement("Apt 101");

        final UserRegistrationDto userRegistrationDto = new UserRegistrationDto("existing_username", "password", profileDto);

        when(userRepository.existsUserByUsernameIgnoreCase("existing_username")).thenReturn(true);

        // Perform the registration and assert DuplicateUsernameException is thrown
        assertThrows(ResourceAlreadyExistsException.class, () -> userManager.registerUser(userRegistrationDto));
    }

    @Test
    public void test_registerUser_DuplicateUsername() {
        // Prepare test data
        final ProfileDto profileDto = new ProfileDto();
        profileDto.setFirstname("John");
        profileDto.setLastname("Doe");
        profileDto.setPhone("+1234567890");
        profileDto.setCountry("USA");
        profileDto.setState("California");
        profileDto.setCity("Los Angeles");
        profileDto.setZipCode("90001");
        profileDto.setStreet("123 Main St");
        profileDto.setComplement("Apt 101");

        final UserRegistrationDto userRegistrationDto = new UserRegistrationDto("existing_username", "password", profileDto);

        when(userRepository.existsUserByUsernameIgnoreCase("existing_username")).thenReturn(true);

        // Perform the registration and assert DuplicateUsernameException is thrown
        assertThrows(ResourceAlreadyExistsException.class, () -> userManager.registerUser(userRegistrationDto));
    }

    @Test
    public void test_retrieve_non_existing_user_by_username() {
        // Prepare test data
        final String username = "non_existing_username";
        when(userRepository.findUserByUsernameIgnoreCase(username)).thenReturn(Optional.empty());

        // Perform the retrieval and assert ResourceNotFoundException is thrown
        assertThrows(ResourceNotFoundException.class, () -> userManager.getUserOrDie(username));
    }

    @Test
    public void test_register_user_with_null_profile() throws RoleNotFoundException {
        // Prepare test data
        final UserRegistrationDto userRegistrationDto = new UserRegistrationDto("new_username", "password", null);

        final User user = new User("new_username", "password");
        when(userRepository.existsUserByUsernameIgnoreCase("new_username")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));

        // Perform the registration
        final User result = userManager.registerUser(userRegistrationDto);

        // Assert the user is registered with an empty profile
        assertEquals(user, result);
        assertNull(result.getProfile());
    }

    @Test
    public void test_register_user_with_empty_profile() throws RoleNotFoundException {
        // Prepare test data
        final ProfileDto profileDto = new ProfileDto();

        final UserRegistrationDto userRegistrationDto = new UserRegistrationDto("new_username", "password", profileDto);

        final User user = new User("new_username", "password");
        when(userRepository.existsUserByUsernameIgnoreCase("new_username")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);
        final Profile profile = new Profile("", "", "", "", "", "", "", "", user)
                .setPhone("")
                .setComplement("");
        when(profileManager.createProfile(any(User.class), any(ProfileDto.class))).thenReturn(profile);
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));

        // Perform the registration
        final User result = userManager.registerUser(userRegistrationDto);

        // Assert the result
        assertEquals(user, result);
    }
}