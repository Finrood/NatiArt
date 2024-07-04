package com.saas.directory.service;

import com.saas.directory.controller.helper.ResourceAlreadyExistsException;
import com.saas.directory.controller.helper.ResourceNotFoundException;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.model.Profile;
import com.saas.directory.model.Role;
import com.saas.directory.model.RoleName;
import com.saas.directory.model.User;
import com.saas.directory.repository.RoleRepository;
import com.saas.directory.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.management.relation.RoleNotFoundException;
import java.util.Optional;

@Service
public class UserManager {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final ProfileManager profileManager;
	private final PasswordEncoder passwordEncoder;

	public UserManager(UserRepository userRepository,
					   RoleRepository roleRepository,
					   ProfileManager profileManager,
					   PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
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

		if (! StringUtils.hasText(userRegistrationDto.password())) {
			throw new IllegalArgumentException("Password cannot be empty");
		}
		final Role role = roleRepository.findRoleByLabel(RoleName.USER)
				.orElseThrow(() -> new RoleNotFoundException(String.format("Role [%s] not found", RoleName.USER)));

		final User newUser = userRepository.save(
				new User(userRegistrationDto.username(), userRegistrationDto.password())
						.setRole(role)
		);
		final Profile profile = profileManager.createProfile(newUser, userRegistrationDto.profile());
		newUser.setProfile(profile);
		return userRepository.save(newUser);
	}
}
