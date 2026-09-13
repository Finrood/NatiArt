package com.saas.directory.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.service.AsaasApiException;
import com.saas.directory.service.AsaasProvisioningService;

@ExtendWith(MockitoExtension.class)
public class UserRegistrationListenerTest {

    @Mock
    private AsaasProvisioningService provisioningService;

    @InjectMocks
    private UserRegistrationListener userRegistrationListener;

    @Test
    void handleUserRegistration_startsDurableProvisioning() {
        UserRegisteredEvent event = new UserRegisteredEvent("testuser");
        assertDoesNotThrow(() -> userRegistrationListener.handleUserRegistration(event));
        verify(provisioningService).provisionUser("testuser");
    }

    @Test
    void handleUserRegistration_shouldThrowException_when_AsaasCallFails() throws Exception {
        // --- Arrange ---
        UserRegisteredEvent event = new UserRegisteredEvent("testuser");
        doThrow(new RuntimeException("Asaas service unavailable"))
                .when(provisioningService)
                .provisionUser("testuser");

        // --- Act & Assert ---
        // This test is now correct because the listener's try-catch is removed.
        // It correctly verifies that the method propagates the exception.
        assertThrows(RuntimeException.class, () -> {
            userRegistrationListener.handleUserRegistration(event);
        });

        // Verify that the process stopped before saving an ID
        verify(provisioningService).provisionUser("testuser");
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
        verifyNoInteractions(provisioningService);
    }

    @Test
    void recover_shouldCompleteWithoutSideEffects_onPermanentProviderFailure() {
        // Arrange
        UserRegisteredEvent event = new UserRegisteredEvent("faileduser");
        AsaasApiException badRequest = new AsaasApiException(
                "Customer registration failed at the payment provider",
                org.springframework.http.HttpStatus.BAD_REQUEST);

        // Act
        userRegistrationListener.recover(badRequest, event);

        // Assert: the unrecoverable branch also only logs
        verifyNoInteractions(provisioningService);
    }
}
