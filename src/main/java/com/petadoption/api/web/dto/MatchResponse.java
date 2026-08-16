package com.petadoption.api.web.dto;

import com.petadoption.api.service.CompatibilityService;

import java.util.List;

/**
 * A pet in the "I want to adopt" result, with the score broken down.
 *
 * <p>Returning the factors rather than just the number is what lets a client
 * explain why that animal showed up where it did -- and lets the person disagree
 * with an argument.
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
