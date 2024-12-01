package com.saas.directory.service;

import com.saas.directory.controller.helper.ResourceAlreadyExistsException;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.model.*;
import com.saas.directory.model.helper.PaymentProcessor;
import com.saas.directory.repository.ExternalUserRepository;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.management.relation.RoleNotFoundException;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserManager {
    private final UserRepository userRepository;
    private final ExternalUserRepository externalUserRepository;
    private final RoleRepository roleRepository;
    private final ProfileManager profileManager;
    private final PasswordEncoder passwordEncoder;

    public UserManager(UserRepository userRepository,
                       ExternalUserRepository externalUserRepository,
                       RoleRepository roleRepository,
                       ProfileManager profileManager,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.externalUserRepository = externalUserRepository;
        this.roleRepository = roleRepository;
        this.profileManager = profileManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public boolean userExist(String username) {
        return userRepository.existsUserByUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUser(String username) {
        return userRepository.findUserByUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public User getUserOrDie(String username) {
        return getUser(username)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("User [%s] not found", username)));
    }

    @Transactional
    public User registerUser(UserRegistrationDto userRegistrationDto) throws RoleNotFoundException {
        if (userExist(userRegistrationDto.username())) {
            throw new ResourceAlreadyExistsException(String.format("User [%s] already exist", userRegistrationDto.username()));
        }

        if (!StringUtils.hasText(userRegistrationDto.password())) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        final Role role = roleRepository.findRoleByLabel(RoleName.USER)
                .orElseThrow(() -> new RoleNotFoundException(String.format("Role [%s] not found", RoleName.USER)));

        final User newUser = userRepository.save(
                new User(userRegistrationDto.username().trim(), userRegistrationDto.password())
                        .setRole(role)
        );
        final Profile profile = profileManager.createProfile(newUser, userRegistrationDto.profile());
        newUser.setProfile(profile);
        return userRepository.save(newUser);
    }

    @Transactional
    public User registerGhostUser(UserRegistrationDto userRegistrationDto) throws RoleNotFoundException {
        if (userExist(userRegistrationDto.username())) {
            throw new ResourceAlreadyExistsException(String.format("User [%s] already exist", userRegistrationDto.username()));
        }

        final Role role = roleRepository.findRoleByLabel(RoleName.USER)
                .orElseThrow(() -> new RoleNotFoundException(String.format("Role [%s] not found", RoleName.USER)));

        final User newUser = userRepository.save(
                new User(userRegistrationDto.username().trim(), UUID.randomUUID().toString())
                        .setRole(role)
                        .setUserType(UserType.GHOST)
        );
        final Profile profile = profileManager.createProfile(newUser, userRegistrationDto.profile());
        newUser.setProfile(profile);
        return userRepository.save(newUser);
    }

    @Transactional(readOnly = true)
    public Optional<ExternalUser> getAsaasCustomer(String username) {
        return externalUserRepository.findByUser(getUserOrDie(username));
    }

    @Transactional(readOnly = true)
    public ExternalUser getAsaasCustomerOrDie(String username) {
        return getAsaasCustomer(username)
                .orElseThrow(() -> new ResourceNotFoundException(String.format("Asaas customer [%s] not found", username)));
    }

    @Transactional
    public ExternalUser addAsaasCustomerIdToUser(String username, String asaasCustomerId) {
        final User user = getUserOrDie(username);
        return externalUserRepository.save(new ExternalUser(user, PaymentProcessor.ASAAS, asaasCustomerId));
    }
}
