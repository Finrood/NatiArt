package com.saas.directory.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import javax.management.relation.RoleNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.saas.directory.controller.helper.ResourceAlreadyExistsException;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.ProfileDto;
import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.model.ExternalUser;
import com.saas.directory.model.Profile;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.model.UserType;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class UserManagerTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExternalUserRepository externalUserRepository = mock(ExternalUserRepository.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final ProfileManager profileManager = mock(ProfileManager.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AsaasUserManager asaasUserManager = mock(AsaasUserManager.class);

    @Captor
    private ArgumentCaptor<UserRegisteredEvent> eventCaptor;

    private UserManager userManager;

    @BeforeEach
    public void initContext() {
        userManager = new UserManager(
                userRepository,
                externalUserRepository,
                roleRepository,
                profileManager,
                eventPublisher,
                asaasUserManager);
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
        final Profile profile = new Profile(
                        "John",
                        "Doe",
                        "00000000011",
                        "USA",
                        "USA",
                        "Los Angeles",
                        "Campinas",
                        "90001",
                        "123 Main St",
                        user)
                .setPhone("+1234567890")
                .setComplement("Apt 101");
        when(profileManager.createProfile(any(User.class), any(ProfileDto.class)))
                .thenReturn(profile);
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));

        // Perform the registration
        final User result = userManager.registerUser(userRegistrationDto);

        assertEquals(user, result);
        assertEquals("new_username", result.getUsername());

        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        UserRegisteredEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("new_username", capturedEvent.username());
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
        final Profile profile = new Profile(
                        "John",
                        "Doe",
                        "00000000011",
                        "USA",
                        "USA",
                        "Los Angeles",
                        "Campinas",
                        "90001",
                        "123 Main St",
                        user)
                .setPhone("+1234567890")
                .setComplement("Apt 101");
        when(profileManager.createProfile(any(User.class), any(ProfileDto.class)))
                .thenReturn(profile);
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));

        // Perform the registration
        final User result = userManager.registerUser(userRegistrationDto);

        assertEquals(user, result);
        assertEquals("new_username", result.getUsername());

        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        UserRegisteredEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("new_username", capturedEvent.username());
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

        final UserRegistrationDto userRegistrationDto =
                new UserRegistrationDto("existing_username", "password", profileDto);

        when(userRepository.existsUserByUsernameIgnoreCase("existing_username")).thenReturn(true);

        // Perform the registration and assert DuplicateUsernameException is thrown
        assertThrows(ResourceAlreadyExistsException.class, () -> userManager.registerUser(userRegistrationDto));
        verify(eventPublisher, never()).publishEvent(any());
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
        assertEquals("new_username", result.getUsername());

        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        UserRegisteredEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("new_username", capturedEvent.username());
    }

    @Test
    public void test_register_user_with_empty_profile() throws RoleNotFoundException {
        // Prepare test data
        final ProfileDto profileDto = new ProfileDto();

        final UserRegistrationDto userRegistrationDto = new UserRegistrationDto("new_username", "password", profileDto);

        final User user = new User("new_username", "password");
        when(userRepository.existsUserByUsernameIgnoreCase("new_username")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);
        final Profile profile = new Profile("", "", "", "", "", "", "", "", "", user)
                .setPhone("")
                .setComplement("");
        when(profileManager.createProfile(any(User.class), any(ProfileDto.class)))
                .thenReturn(profile);
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));

        // Perform the registration
        final User result = userManager.registerUser(userRegistrationDto);

        // Assert the result
        assertEquals(user, result);
        assertNotNull(result.getProfile());
        assertEquals("new_username", result.getUsername());

        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());

        UserRegisteredEvent capturedEvent = eventCaptor.getValue();
        assertNotNull(capturedEvent);
        assertEquals("new_username", capturedEvent.username());
    }

    @Test
    public void registerGhostUser_existingGhostEmail_throwsWithoutSideEffects() {
        final User existingGhost = new User("victim@example.com", "unseen-random-password")
                .setRole(new Role(RoleName.USER))
                .setUserType(UserType.GHOST);
        when(userRepository.findUserByUsernameIgnoreCase("victim@example.com")).thenReturn(Optional.of(existingGhost));

        final UserRegistrationDto dto = new UserRegistrationDto("victim@example.com", "password", null);

        assertThrows(ResourceAlreadyExistsException.class, () -> userManager.registerGhostUser(dto));
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository, profileManager, asaasUserManager, externalUserRepository, eventPublisher);
    }

    @Test
    public void registerGhostUser_existingUserEmail_throwsWithoutSideEffects() {
        final User existingUser = new User("taken@example.com", "password").setRole(new Role(RoleName.USER));
        when(userRepository.findUserByUsernameIgnoreCase("taken@example.com")).thenReturn(Optional.of(existingUser));

        final UserRegistrationDto dto = new UserRegistrationDto("taken@example.com", "password", null);

        assertThrows(ResourceAlreadyExistsException.class, () -> userManager.registerGhostUser(dto));
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(roleRepository, profileManager, asaasUserManager, externalUserRepository, eventPublisher);
    }

    @Test
    public void registerGhostUser_freshEmail_createsGhostAndProvisionsAsaasCustomer() throws Exception {
        final Role userRole = new Role(RoleName.USER);
        final User persistedGhost = new User("new-ghost@example.com", "random-uuid-password")
                .setRole(userRole)
                .setUserType(UserType.GHOST);
        when(userRepository.findUserByUsernameIgnoreCase("new-ghost@example.com"))
                .thenReturn(Optional.empty(), Optional.of(persistedGhost));
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        final Profile profile = new Profile(
                        "John",
                        "Doe",
                        "00000000011",
                        "USA",
                        "USA",
                        "Los Angeles",
                        "Campinas",
                        "90001",
                        "123 Main St",
                        persistedGhost)
                .setPhone("+1234567890")
                .setComplement("Apt 101");
        when(profileManager.createProfile(any(User.class), any(ProfileDto.class)))
                .thenReturn(profile);
        final AsaasCustomerCreationResponse asaasResponse = new AsaasCustomerCreationResponse(
                "customer",
                "cus_12345",
                "2025-01-01",
                "Test User",
                "test@test.com",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                0,
                null,
                null,
                null);
        when(asaasUserManager.registerUser(any(UserDto.class))).thenReturn(asaasResponse);

        final User result =
                userManager.registerGhostUser(new UserRegistrationDto("new-ghost@example.com", "password", null));

        assertEquals("new-ghost@example.com", result.getUsername());
        assertEquals(UserType.GHOST, result.getUserType());
        verify(asaasUserManager, times(1)).registerUser(any(UserDto.class));
        verify(userRepository, times(2)).save(any(User.class));
        verify(externalUserRepository, times(1)).save(any(ExternalUser.class));
        verify(eventPublisher, never()).publishEvent(any());
    }
}
