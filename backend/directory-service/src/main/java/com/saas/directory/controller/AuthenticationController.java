package com.saas.directory.controller;


import com.saas.directory.configuration.UserAuthenticationProvider;
import com.saas.directory.dto.CredentialsDto;
import com.saas.directory.dto.UserAuthDto;
import com.saas.directory.service.AuthenticationManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class AuthenticationController {
	private final AuthenticationManager authenticationManager;
	private final UserAuthenticationProvider userAuthenticationProvider;

	public AuthenticationController(AuthenticationManager authenticationManager, UserAuthenticationProvider userAuthenticationProvider) {
		this.authenticationManager = authenticationManager;
		this.userAuthenticationProvider = userAuthenticationProvider;
	}

	@PostMapping("/login")
	public ResponseEntity<UserAuthDto> authenticateUser(@RequestBody CredentialsDto credentialsDto) {
		final UserAuthDto userAuthDto = authenticationManager.login(credentialsDto);
		return ResponseEntity.ok(userAuthDto);
	}

	@PostMapping("/refresh-token")
	public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException, IllegalAccessException {
		userAuthenticationProvider.refreshToken(request, response);
	}

	@PostMapping("/signout")
	public ResponseEntity<Boolean> logout(HttpServletRequest request) {
		authenticationManager.logout(request);
		return ResponseEntity.ok(true);
	}
}
