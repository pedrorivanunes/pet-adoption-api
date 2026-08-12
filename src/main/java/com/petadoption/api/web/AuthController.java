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
	 * A autenticação passa pelo {@link AuthenticationManager} em vez de comparar
	 * o hash na mão: assim o fluxo herda o tratamento padrão do Spring Security
	 * (inclusive proteção contra enumeração por tempo de resposta), e a falha
	 * vira uma AuthenticationException — que o handler traduz para 401.
	 */
	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.email(), request.password()));

		TokenService.IssuedToken token = tokens.issue((UserDetails) authentication.getPrincipal());
		return TokenResponse.bearer(token.value(), token.expiresInSeconds());
	}
}
