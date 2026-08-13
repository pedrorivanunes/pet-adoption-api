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
 * Acompanhamento pós-adoção, ancorado no pet.
 *
 * <p>O recurso conceitual é a adoção, mas a alça que abrigo e adotante têm em
 * mãos é o animal. Expor ids de adoção exigiria endpoints só para descobri-los,
 * e a adoção vigente de um pet é sempre determinável — a mais recente.
 */
@RestController
@RequestMapping("/api/pets/{petId}")
public class FollowUpController {

	private final FollowUpService followUps;

	public FollowUpController(FollowUpService followUps) {
		this.followUps = followUps;
	}

	/** Registrar contato é de quem entregou o animal, não de quem o recebeu. */
	@PostMapping("/followups")
	@ResponseStatus(HttpStatus.CREATED)
	public FollowUpResponse record(@PathVariable Long petId,
			@Valid @RequestBody FollowUpRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return FollowUpResponse.from(followUps.record(petId, request.toData(), jwt.getSubject()));
	}

	/** Relatório do período mínimo de acompanhamento. */
	@GetMapping("/followup-report")
	public FollowUpReportResponse report(@PathVariable Long petId, @AuthenticationPrincipal Jwt jwt) {
		return FollowUpReportResponse.from(followUps.report(petId, jwt.getSubject()));
	}
}
