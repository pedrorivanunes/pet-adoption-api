package com.petadoption.api.web;

import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.service.PetService;
import com.petadoption.api.web.dto.CreatePetRequest;
import com.petadoption.api.web.dto.PageResponse;
import com.petadoption.api.web.dto.PetResponse;
import com.petadoption.api.web.dto.UpdatePetRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pets")
public class PetController {

	private final PetService pets;

	public PetController(PetService pets) {
		this.pets = pets;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PetResponse create(@Valid @RequestBody CreatePetRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return PetResponse.from(
				pets.create(request.toData(), request.ownerOrganizationId(), jwt.getSubject()));
	}

	/** Catálogo público: por padrão, quem está disponível para adoção. */
	@GetMapping
	public PageResponse<PetResponse> list(
			@RequestParam(required = false) PetStatus status,
			@RequestParam(required = false) String species,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

		return PageResponse.from(pets.list(status, species, pageable), PetResponse::from);
	}

	@GetMapping("/{id}")
	public PetResponse getById(@PathVariable Long id) {
		return PetResponse.from(pets.getById(id));
	}

	@PutMapping("/{id}")
	public PetResponse update(@PathVariable Long id,
			@Valid @RequestBody UpdatePetRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return PetResponse.from(pets.update(id, request.toData(), jwt.getSubject()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		pets.delete(id, jwt.getSubject());
	}
}
