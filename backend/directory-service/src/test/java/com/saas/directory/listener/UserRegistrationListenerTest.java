package com.saas.directory.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.saas.directory.event.UserRegisteredEvent;
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
    void failedWakeUpDoesNotEscapeToAsyncCaller() {
        UserRegisteredEvent event = new UserRegisteredEvent("testuser");
        doThrow(new RuntimeException("Asaas service unavailable"))
                .when(provisioningService)
                .provisionUser("testuser");

        assertDoesNotThrow(() -> userRegistrationListener.handleUserRegistration(event));
        verify(provisioningService).provisionUser("testuser");
    }
}
