package com.saas.directory.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CredentialsDtoValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private static Set<String> violatedFields(CredentialsDto dto) {
        return validator.validate(dto).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void blankUsernameIsRejected() {
        final CredentialsDto dto = new CredentialsDto("   ", "password");

        assertEquals(Set.of("username"), violatedFields(dto));
    }

    @Test
    void nonEmailUsernameIsRejected() {
        final CredentialsDto dto = new CredentialsDto("not-an-email", "password");

        assertEquals(Set.of("username"), violatedFields(dto));
    }

    @Test
    void crlfBearingUsernameIsRejectedBeforeLogging() {
        final CredentialsDto dto = new CredentialsDto("victim@example.com\nFORGED: admin logged out", "password");

        assertEquals(Set.of("username"), violatedFields(dto));
    }

    @Test
    void overlongUsernameIsRejected() {
        final String overlong = "a".repeat(250) + "@example.com";
        final CredentialsDto dto = new CredentialsDto(overlong, "password");

        assertEquals(Set.of("username"), violatedFields(dto));
    }

    @Test
    void blankPasswordIsRejected() {
        final CredentialsDto dto = new CredentialsDto("john@example.com", "  ");

        assertEquals(Set.of("password"), violatedFields(dto));
    }

    @Test
    void validCredentialsHaveNoViolations() {
        final CredentialsDto dto = new CredentialsDto("john@example.com", "password");

        assertTrue(violatedFields(dto).isEmpty());
    }
}
