package com.petadoption.api.web.dto;

import com.petadoption.api.service.CompatibilityService;

import java.util.List;

/**
 * Um pet do resultado de "quero adotar", com a decomposição do score.
 *
 * <p>Devolver os fatores, e não só o número, é o que permite à interface
 * explicar por que aquele animal apareceu ali — e à pessoa discordar com
 * argumento.
 */
public record MatchResponse(PetResponse pet, int score, List<FactorResponse> factors) {

	public record FactorResponse(String category, int points, String detail) {
	}

	public static MatchResponse from(CompatibilityService.Match match) {
		return new MatchResponse(
				PetResponse.from(match.pet()),
				match.compatibility().score(),
				match.compatibility().factors().stream()
						.map(factor -> new FactorResponse(
								factor.category(), factor.points(), factor.detail()))
						.toList());
	}

}
