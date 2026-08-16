package com.petadoption.api.web;

import com.petadoption.api.security.TokenService;
import com.petadoption.api.service.UserService;
import com.petadoption.api.web.dto.LoginRequest;
import com.petadoption.api.web.dto.RegisterRequest;
import com.petadoption.api.web.dto.TokenResponse;
import com.petadoption.api.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService users;
	private final AuthenticationManager authenticationManager;
	private final TokenService tokens;

	public AuthController(UserService users, AuthenticationManager authenticationManager, TokenService tokens) {
		this.users = users;
		this.authenticationManager = authenticationManager;
		this.tokens = tokens;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		return UserResponse.from(users.register(new UserService.NewUser(
				request.name(), request.email(), request.password(), request.phone())));
	}

	/**
	 * Authentication goes through {@link AuthenticationManager} rather than
	 * comparing the hash by hand: this way the flow inherits Spring Security's
	 * standard handling (including protection against timing-based account
	 * enumeration), and a failure becomes an AuthenticationException -- which the
	 * handler translates into a 401.
	 */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		TokenService.IssuedToken token = tokens.issue((UserDetails) authentication.getPrincipal());
		return TokenResponse.bearer(token.value(), token.expiresInSeconds());
	}
}
