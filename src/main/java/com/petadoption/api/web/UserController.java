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
	 * The authenticated user's own data.
	 *
	 * <p>The identity comes from the token's subject, never from an id supplied
	 * by the client: that is what stops changing a number in the URL from reading
	 * somebody else's record.
	 */
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		return UserResponse.from(users.getByEmail(jwt.getSubject()));
	}

	/**
	 * The authenticated person's pets.
	 *
	 * <p>It sits under {@code /api/users/me/} on purpose, and not at
	 * {@code /api/pets/me}: the pet catalogue is public, and a personal route
	 * hanging beneath it would end up exposed along with it by any wildcard
	 * security rule.
	 */
	@GetMapping("/me/pets")
	public PageResponse<PetResponse> myPets(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20) Pageable pageable) {

		return PageResponse.from(pets.listOwnedBy(jwt.getSubject(), pageable), PetResponse::from);
	}
}
