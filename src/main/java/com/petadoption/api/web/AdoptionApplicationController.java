package com.petadoption.api.web;

import com.petadoption.api.service.AdoptionApplicationService;
import com.petadoption.api.service.CompatibilityService;
import com.petadoption.api.web.dto.ApplicationResponse;
import com.petadoption.api.web.dto.CreateApplicationRequest;
import com.petadoption.api.web.dto.DecisionRequest;
import com.petadoption.api.web.dto.MatchResponse;
import com.petadoption.api.web.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AdoptionApplicationController {

	private final AdoptionApplicationService applications;
	private final CompatibilityService compatibility;

	public AdoptionApplicationController(AdoptionApplicationService applications,
			CompatibilityService compatibility) {
		this.applications = applications;
		this.compatibility = compatibility;
	}

	/**
	 * "I want to adopt": available pets ranked by how well they match the
	 * authenticated person. Animals with a blocker do not appear.
	 */
	@GetMapping("/adoptions/matches")
	public PageResponse<MatchResponse> matches(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20) Pageable pageable) {

		return PageResponse.from(
				compatibility.matchesFor(jwt.getSubject(), pageable), MatchResponse::from);
	}

	@PostMapping("/adoptions/applications")
	@ResponseStatus(HttpStatus.CREATED)
	public ApplicationResponse apply(@Valid @RequestBody CreateApplicationRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return ApplicationResponse.from(
				applications.apply(request.petId(), request.message(), jwt.getSubject()));
	}

	@GetMapping("/adoptions/applications/me")
	public PageResponse<ApplicationResponse> mine(@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20) Pageable pageable) {

		return PageResponse.from(applications.listMine(jwt.getSubject(), pageable), ApplicationResponse::from);
	}

	@GetMapping("/adoptions/applications/{id}")
	public ApplicationResponse getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return ApplicationResponse.from(applications.getById(id, jwt.getSubject()));
	}

	/**
	 * A pet's compatibility report: the applications received, ranked by
	 * affinity, visible only to whoever manages the animal.
	 *
	 * <p>The ranking covers the people who applied, not the whole adopter base.
	 * An adopter profile holds housing, children and how many people live in the
	 * home -- information a shelter only has reason to see once that person has
	 * come forward for that animal.
	 */
	@GetMapping("/pets/{petId}/applications")
	public PageResponse<ApplicationResponse> forPet(@PathVariable Long petId,
			@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20, sort = "compatibilityScore",
					direction = Sort.Direction.DESC) Pageable pageable) {

		return PageResponse.from(
				applications.listForPet(petId, jwt.getSubject(), pageable), ApplicationResponse::from);
	}

	// Decisions are POSTs on sub-resources rather than a PATCH of the status:
	// approving is not "write APPROVED into the field", it is an event that
	// moves the pet, creates the adoption and rejects the other applicants.
	@PostMapping("/adoptions/applications/{id}/approve")
	public ApplicationResponse approve(@PathVariable Long id,
			@Valid @RequestBody(required = false) DecisionRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return ApplicationResponse.from(applications.approve(id, noteOf(request), jwt.getSubject()));
	}

	@PostMapping("/adoptions/applications/{id}/reject")
	public ApplicationResponse reject(@PathVariable Long id,
			@Valid @RequestBody(required = false) DecisionRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return ApplicationResponse.from(applications.reject(id, noteOf(request), jwt.getSubject()));
	}

	@PostMapping("/adoptions/applications/{id}/cancel")
	public ApplicationResponse cancel(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return ApplicationResponse.from(applications.cancel(id, jwt.getSubject()));
	}

	private String noteOf(DecisionRequest request) {
		return request == null ? null : request.note();
	}
}
