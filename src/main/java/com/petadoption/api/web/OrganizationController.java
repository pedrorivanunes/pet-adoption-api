package com.petadoption.api.web;

import com.petadoption.api.service.OrganizationService;
import com.petadoption.api.service.PetService;
import com.petadoption.api.web.dto.MemberRequest;
import com.petadoption.api.web.dto.MemberResponse;
import com.petadoption.api.web.dto.MemberRoleRequest;
import com.petadoption.api.web.dto.OrganizationRequest;
import com.petadoption.api.web.dto.OrganizationResponse;
import com.petadoption.api.web.dto.PageResponse;
import com.petadoption.api.web.dto.PetResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

	private final OrganizationService organizations;
	private final PetService pets;

	public OrganizationController(OrganizationService organizations, PetService pets) {
		this.organizations = organizations;
		this.pets = pets;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrganizationResponse create(@Valid @RequestBody OrganizationRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return OrganizationResponse.from(organizations.create(toData(request), jwt.getSubject()));
	}

	@GetMapping
	public PageResponse<OrganizationResponse> list(@PageableDefault(size = 20) Pageable pageable) {
		return PageResponse.from(organizations.list(pageable), OrganizationResponse::from);
	}

	@GetMapping("/{id}")
	public OrganizationResponse getById(@PathVariable Long id) {
		return OrganizationResponse.from(organizations.getById(id));
	}

	@PutMapping("/{id}")
	public OrganizationResponse update(@PathVariable Long id,
			@Valid @RequestBody OrganizationRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return OrganizationResponse.from(organizations.update(id, toData(request), jwt.getSubject()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		organizations.delete(id, jwt.getSubject());
	}

	/** The shelter's pets -- part of the catalogue, therefore public. */
	@GetMapping("/{id}/pets")
	public PageResponse<PetResponse> pets(@PathVariable Long id,
			@PageableDefault(size = 20) Pageable pageable) {

		organizations.getById(id);
		return PageResponse.from(pets.listOfOrganization(id, pageable), PetResponse::from);
	}

	// ======================================================= members =========

	@GetMapping("/{id}/members")
	public List<MemberResponse> members(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return organizations.listMembers(id, jwt.getSubject()).stream()
				.map(MemberResponse::from)
				.toList();
	}

	@PostMapping("/{id}/members")
	@ResponseStatus(HttpStatus.CREATED)
	public MemberResponse addMember(@PathVariable Long id,
			@Valid @RequestBody MemberRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return MemberResponse.from(
				organizations.addMember(id, request.email(), request.role(), jwt.getSubject()));
	}

	@PutMapping("/{id}/members/{userId}")
	public MemberResponse changeMemberRole(@PathVariable Long id,
			@PathVariable Long userId,
			@Valid @RequestBody MemberRoleRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return MemberResponse.from(
				organizations.changeMemberRole(id, userId, request.role(), jwt.getSubject()));
	}

	@DeleteMapping("/{id}/members/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeMember(@PathVariable Long id, @PathVariable Long userId,
			@AuthenticationPrincipal Jwt jwt) {

		organizations.removeMember(id, userId, jwt.getSubject());
	}

	private OrganizationService.OrganizationData toData(OrganizationRequest request) {
		return new OrganizationService.OrganizationData(
				request.name(), request.description(), request.email(), request.phone(), request.address());
	}
}
