package com.saas.directory.service;

import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthenticationManagerTest {
    private final UserManager userManager = mock(UserManager.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserAuthenticationProvider userAuthenticationProvider = mock(UserAuthenticationProvider.class);

    private AuthenticationManager authenticationManager;

    @BeforeEach
    public void initContext() {
         authenticationManager = new AuthenticationManager(
                 userManager,
                 passwordEncoder,
                 userAuthenticationProvider
         );
    }

    // Should handle null password in credentialsDto and throw UserNotFoundException for wrong password
    @Test
    public void test_null_password_in_credentialsDto() {
        final String username = "testUser";
        final CredentialsDto credentialsDto = new CredentialsDto(username, null);

        final User user = new User(username, "testPassword");
        when(userManager.getUserOrDie(username)).thenReturn(user);

        // Act and Assert
        final ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> authenticationManager.login(credentialsDto));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("Password is invalid. Try again", exception.getMessage());
    }
}