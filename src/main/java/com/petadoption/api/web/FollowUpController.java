package com.petadoption.api.web;

import com.petadoption.api.service.FollowUpService;
import com.petadoption.api.web.dto.FollowUpReportResponse;
import com.petadoption.api.web.dto.FollowUpRequest;
import com.petadoption.api.web.dto.FollowUpResponse;
import jakarta.validation.Valid;
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

/**
 * Post-adoption follow-up, anchored on the pet.
 *
 * <p>The conceptual resource is the adoption, but the handle a shelter and an
 * adopter actually hold is the animal. Exposing adoption ids would require
 * endpoints just to discover them, and a pet's current adoption is always
 * determinable -- it is the most recent one.
 */
@RestController
@RequestMapping("/api/pets/{petId}")
public class FollowUpController {

	private final FollowUpService followUps;

	public FollowUpController(FollowUpService followUps) {
		this.followUps = followUps;
	}

	/** Logging a contact belongs to whoever handed the animal over, not whoever received it. */
	@PostMapping("/followups")
	@ResponseStatus(HttpStatus.CREATED)
	public FollowUpResponse record(@PathVariable Long petId,
			@Valid @RequestBody FollowUpRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return FollowUpResponse.from(followUps.record(petId, request.toData(), jwt.getSubject()));
	}

	/** Report for the minimum follow-up period. */
	@GetMapping("/followup-report")
	public FollowUpReportResponse report(@PathVariable Long petId, @AuthenticationPrincipal Jwt jwt) {
		return FollowUpReportResponse.from(followUps.report(petId, jwt.getSubject()));
	}
}
