package com.saas.directory.controller;

import com.saas.directory.dto.UserDto;
import com.saas.directory.dto.UserRegistrationDto;
import com.saas.directory.service.UserManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.management.relation.RoleNotFoundException;

@RestController
public class UserRegistrationController {
	private final UserManager userManager;

	public UserRegistrationController(UserManager userManager) {
		this.userManager = userManager;
	}

	@PostMapping("/register-user")
	public ResponseEntity<UserDto> registerUser(@RequestBody UserRegistrationDto userRegistrationDto) throws RoleNotFoundException {
		final UserDto userDto = UserDto.from(userManager.registerUser(userRegistrationDto));
		return ResponseEntity.ok(userDto);
	}
}
