package com.saas.directory.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.service.AsaasApiException;
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

    private ListAppender<ILoggingEvent> listAppender;

    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = new Role(RoleName.USER);
        testUser = new User("testuser", "password");
        testUser.setRole(role);

        listAppender = new ListAppender<>();
        listAppender.start();
        ((Logger) LoggerFactory.getLogger(UserRegistrationListener.class)).addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(UserRegistrationListener.class)).detachAppender(listAppender);
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
        // The unknown/transient failure must page ops with the CRITICAL signal
        assertLogEvent("CRITICAL", true);
        assertLogEvent("will not be retried", false);
    }

    @Test
    void recover_shouldLogPermanentBranchNotCritical_onMappedAsaas4xx() {
        // Arrange: a mapped AsaasApiException is what actually reaches the recover
        // (raw HttpClientErrorException instances are mapped away inside AsaasUserManager)
        UserRegisteredEvent event = new UserRegisteredEvent("faileduser");
        AsaasApiException permanent = new AsaasApiException(
                "Customer registration failed at the payment provider", HttpStatus.BAD_REQUEST);

        // Act
        userRegistrationListener.recover(permanent, event);

        // Assert: permanent client errors log the targeted branch, never CRITICAL
        verifyNoInteractions(userManager, asaasUserManager);
        assertLogEvent("will not be retried", true);
        assertLogEvent("CRITICAL", false);
    }

    @Test
    void recover_shouldLogCritical_onRawHttpClientError() {
        // Arrange: a raw HttpClientErrorException is treated as unknown/transient —
        // it never reaches the recover in the handler's call graph, so it must not
        // take the permanent branch
        UserRegisteredEvent event = new UserRegisteredEvent("faileduser");
        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);

        // Act
        userRegistrationListener.recover(badRequest, event);

        // Assert: the unknown failure also only logs, on the CRITICAL branch
        verifyNoInteractions(userManager, asaasUserManager);
        assertLogEvent("CRITICAL", true);
    }

    private void assertLogEvent(String messageFragment, boolean expected) {
        final boolean found = listAppender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(messageFragment));
        assertEquals(expected, found, "Expected log fragment \"" + messageFragment + "\" present=" + expected);
    }
}
