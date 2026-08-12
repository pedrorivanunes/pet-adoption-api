package com.petadoption.api.web;

import com.petadoption.api.service.UserService;
import com.petadoption.api.web.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService users;

	public UserController(UserService users) {
		this.users = users;
	}

	/**
	 * Dados do usuário autenticado.
	 *
	 * <p>A identidade vem do subject do token, nunca de um id recebido do
	 * cliente: é o que impede que trocar um número na URL leia o cadastro de
	 * outra pessoa.
	 */
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return UserResponse.from(users.getByEmail(jwt.getSubject()));
	}
}
