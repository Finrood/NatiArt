package com.saas.directory.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.service.AsaasUserManager;
import com.saas.directory.service.UserManager;

@ExtendWith(MockitoExtension.class)
public class UserRegistrationListenerTest {

    @Mock
    private UserManager userManager;

    @Mock
    private AsaasUserManager asaasUserManager;

    @InjectMocks
    private UserRegistrationListener userRegistrationListener;

    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = new Role(RoleName.USER);
        testUser = new User("testuser", "password");
        testUser.setRole(role);
    }

    @Test
    void handleUserRegistration_shouldCallAsaasAndSaveExternalId_onSuccess() throws Exception {
        // Arrange
        UserRegisteredEvent event = new UserRegisteredEvent("testuser");
        AsaasCustomerCreationResponse asaasResponse = new AsaasCustomerCreationResponse(
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

        when(userManager.getUserOrDie("testuser")).thenReturn(testUser);
        when(asaasUserManager.registerUser(any(UserDto.class))).thenReturn(asaasResponse);

        // Act
        userRegistrationListener.handleUserRegistration(event);

        // Assert
        verify(userManager, times(1)).getUserOrDie("testuser");
        verify(asaasUserManager, times(1)).registerUser(any(UserDto.class));
        verify(userManager, times(1)).addAsaasCustomerIdToUser("testuser", "cus_12345");
    }

    @Test
    void handleUserRegistration_shouldThrowException_when_AsaasCallFails() throws Exception {
        // --- Arrange ---
        UserRegisteredEvent event = new UserRegisteredEvent("testuser");
        when(userManager.getUserOrDie("testuser")).thenReturn(testUser);
        when(asaasUserManager.registerUser(any(UserDto.class)))
                .thenThrow(new RuntimeException("Asaas service unavailable"));

        // --- Act & Assert ---
        // This test is now correct because the listener's try-catch is removed.
        // It correctly verifies that the method propagates the exception.
        assertThrows(RuntimeException.class, () -> {
            userRegistrationListener.handleUserRegistration(event);
        });

        // Verify that the process stopped before saving an ID
        verify(userManager, never()).addAsaasCustomerIdToUser(anyString(), anyString());
    }

    @Test
    void recover_shouldCompleteWithoutSideEffects_onFinalFailure() {
        // Arrange
        UserRegisteredEvent event = new UserRegisteredEvent("faileduser");
        RuntimeException finalException = new RuntimeException("Final failure");

        // Act
        // Directly invoke the recover method to test its internal logic
        userRegistrationListener.recover(finalException, event);

        // Assert: retry exhaustion only logs — no manager interaction, no rethrow
        verifyNoInteractions(userManager, asaasUserManager);
    }

    @Test
    void recover_shouldCompleteWithoutSideEffects_onBadRequest() {
        // Arrange
        UserRegisteredEvent event = new UserRegisteredEvent("faileduser");
        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);

        // Act
        userRegistrationListener.recover(badRequest, event);

        // Assert: the unrecoverable branch also only logs
        verifyNoInteractions(userManager, asaasUserManager);
    }
}
