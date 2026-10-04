package com.saas.directory.controller;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.saas.directory.configuration.ControllerAdvice;
import com.saas.directory.event.UserRegisteredEvent;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.repository.AsaasProvisioningJobRepository;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.ProfileRepository;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;
import com.saas.directory.service.ProfileManager;
import com.saas.directory.service.UserManager;

class UserRegistrationHttpTest {
    private UserRepository userRepository;
    private ProfileRepository profileRepository;
    private RoleRepository roleRepository;
    private ApplicationEventPublisher eventPublisher;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        profileRepository = mock(ProfileRepository.class);
        roleRepository = mock(RoleRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        final ProfileManager profileManager = new ProfileManager(profileRepository);
        final UserManager userManager = new UserManager(
                userRepository,
                mock(ExternalUserRepository.class),
                roleRepository,
                mock(AsaasProvisioningJobRepository.class),
                profileManager,
                eventPublisher);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new UserRegistrationController(userManager))
                .setControllerAdvice(new ControllerAdvice())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSignups")
    void invalidSignupReturns400BeforePersistenceOrProviderEvent(String description, String body) throws Exception {
        mvc.perform(post("/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(profileRepository, eventPublisher);
    }

    private static Stream<Arguments> invalidSignups() {
        return Stream.of(
                arguments("alphabetic CPF", body("Password1", "abc", "12345-678", "Ana", "SP", "")),
                arguments("bad CPF checksum", body("Password1", "12345678900", "12345-678", "Ana", "SP", "")),
                arguments("bad CEP", body("Password1", "12345678909", "12345", "Ana", "SP", "")),
                arguments("overlong name", body("Password1", "12345678909", "12345-678", "A".repeat(101), "SP", "")),
                arguments("short password", body("short", "12345678909", "12345-678", "Ana", "SP", "")),
                arguments(
                        "overlimit multibyte password",
                        body("é".repeat(40), "12345678909", "12345-678", "Ana", "SP", "")),
                arguments("unsupported state", body("Password1", "12345678909", "12345-678", "Ana", "ZZ", "")),
                arguments("invalid phone", body("Password1", "12345678909", "12345-678", "Ana", "SP", "123456789")));
    }

    @Test
    void blankOptionalPhoneAndLowercaseStateRegisterSuccessfully() throws Exception {
        when(roleRepository.findRoleByLabel(RoleName.USER)).thenReturn(Optional.of(new Role(RoleName.USER)));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mvc.perform(post("/register-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Password1", "123.456.789-09", "12345-678", "Ana", "sp", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.cpf").value("12345678909"))
                .andExpect(jsonPath("$.profile.zipCode").value("12345678"))
                .andExpect(jsonPath("$.profile.state").value("SP"))
                .andExpect(jsonPath("$.profile.phone").isEmpty());

        verify(userRepository, times(2)).save(any());
        verify(profileRepository).save(any());
        verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
    }

    private static String body(
            String password, String cpf, String zipCode, String firstname, String state, String phone) {
        return """
                {"username":"ana@example.com","password":"%s","profile":{"firstname":"%s","lastname":"Silva","cpf":"%s","phone":"%s","country":"Brazil","state":"%s","city":"São Paulo","neighborhood":"Centro","zipCode":"%s","street":"Rua Principal"}}
                """.formatted(password, firstname, cpf, phone, state, zipCode);
    }
}
