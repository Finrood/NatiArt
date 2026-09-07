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

class UserRegistrationDtoValidationTest {
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

    private static ProfileDto validProfile() {
        return new ProfileDto()
                .setFirstname("John")
                .setLastname("Doe")
                .setCpf("000.000.000-11")
                .setCountry("USA")
                .setState("California")
                .setCity("Los Angeles")
                .setNeighborhood("Campinas")
                .setZipCode("12345")
                .setStreet("Main Street");
    }

    private static Set<String> violatedFields(UserRegistrationDto dto) {
        return validator.validate(dto).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void blankUsernameIsRejected() {
        final UserRegistrationDto dto = new UserRegistrationDto("   ", "password", validProfile());

        assertEquals(Set.of("username"), violatedFields(dto));
    }

    @Test
    void nonEmailUsernameIsRejected() {
        final UserRegistrationDto dto = new UserRegistrationDto("not-an-email", "password", validProfile());

        assertEquals(Set.of("username"), violatedFields(dto));
    }

    @Test
    void blankPasswordIsRejected() {
        final UserRegistrationDto dto = new UserRegistrationDto("john@example.com", "  ", validProfile());

        assertEquals(Set.of("password"), violatedFields(dto));
    }

    @Test
    void missingProfileIsRejected() {
        final UserRegistrationDto dto = new UserRegistrationDto("john@example.com", "password", null);

        assertEquals(Set.of("profile"), violatedFields(dto));
    }

    @Test
    void blankRequiredProfileFieldIsRejectedViaCascade() {
        final UserRegistrationDto dto = new UserRegistrationDto(
                "john@example.com", "password", validProfile().setFirstname("   "));

        assertEquals(Set.of("profile.firstname"), violatedFields(dto));
    }

    @Test
    void validRegistrationDtoHasNoViolations() {
        final UserRegistrationDto dto = new UserRegistrationDto("john@example.com", "password", validProfile());

        assertTrue(violatedFields(dto).isEmpty());
    }
}
