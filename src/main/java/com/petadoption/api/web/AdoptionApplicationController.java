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
	 * "Quero adotar": pets disponíveis ranqueados por compatibilidade com quem
	 * está autenticado. Animais com fator impeditivo não aparecem.
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
	 * Relatório de compatibilidade de um pet: as candidaturas recebidas,
	 * ranqueadas por afinidade, visíveis só a quem administra o animal.
	 *
	 * <p>O ranking cobre quem se candidatou, e não toda a base de adotantes.
	 * Perfil de adotante tem moradia, filhos e quantas pessoas moram na casa —
	 * informação que um abrigo só tem motivo de ver quando a pessoa procurou por
	 * aquele animal.
	 */
	@GetMapping("/pets/{petId}/applications")
	public PageResponse<ApplicationResponse> forPet(@PathVariable Long petId,
			@AuthenticationPrincipal Jwt jwt,
			@PageableDefault(size = 20, sort = "compatibilityScore",
					direction = Sort.Direction.DESC) Pageable pageable) {

		return PageResponse.from(
				applications.listForPet(petId, jwt.getSubject(), pageable), ApplicationResponse::from);
	}

	// As decisões são POST em sub-recursos, e não um PATCH de status: aprovar
	// não é "escrever APPROVED no campo", é um evento que move o pet, cria a
	// adoção e recusa os demais candidatos.
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
