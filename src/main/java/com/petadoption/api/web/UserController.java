package com.petadoption.api.web;

import com.petadoption.api.service.PetService;
import com.petadoption.api.service.UserService;
import com.petadoption.api.web.dto.PageResponse;
import com.petadoption.api.web.dto.PetResponse;
import com.petadoption.api.web.dto.UserResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService users;
	private final PetService pets;

	public UserController(UserService users, PetService pets) {
		this.users = users;
		this.pets = pets;
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

	/**
	 * Pets de quem está autenticado.
	 *
	 * <p>Fica sob {@code /api/users/me/} de propósito, e não em
	 * {@code /api/pets/me}: o catálogo de pets é público, e uma rota pessoal
	 * pendurada debaixo dele acabaria liberada junto por qualquer regra de
	 * segurança com curinga.
	 */
	@GetMapping("/me/pets")
	public PageResponse<PetResponse> myPets(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20) Pageable pageable) {

		return PageResponse.from(pets.listOwnedBy(jwt.getSubject(), pageable), PetResponse::from);
	}
}
