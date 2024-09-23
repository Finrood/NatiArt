package com.saas.directory.controller;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.dto.asaas.AsaasCustomerCreationResponse;
import com.saas.directory.service.AsaasUserManager;
import com.saas.directory.service.UserManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.management.relation.RoleNotFoundException;

@RestController
public class UserRegistrationController {
	private final UserManager userManager;
	private final AsaasUserManager asaasUserManager;

	public UserRegistrationController(UserManager userManager, AsaasUserManager asaasUserManager) {
		this.userManager = userManager;
		this.asaasUserManager = asaasUserManager;
	}

	@PostMapping("/register-user")
	public ResponseEntity<UserDto> registerUser(@RequestBody UserRegistrationDto userRegistrationDto) throws RoleNotFoundException {
		final UserDto userDto = UserDto.from(userManager.registerUser(userRegistrationDto));
		final AsaasCustomerCreationResponse asaasCustomerCreationResponse = asaasUserManager.registerUser(userDto);
		userDto.setExternalId(userManager.addAsaasCustomerIdToUser(userDto.getUsername(), asaasCustomerCreationResponse.getId()).getExternalId());
		return ResponseEntity.ok(userDto);
	}
}
