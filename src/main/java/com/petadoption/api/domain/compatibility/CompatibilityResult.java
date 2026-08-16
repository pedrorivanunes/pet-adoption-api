package com.petadoption.api.domain.compatibility;

import java.util.List;

/**
 * The result of evaluating compatibility between a pet and an adopter.
 *
 * <p>It carries the breakdown, not just the number. A ranking that says "87" and
 * nothing else is impossible to check and impossible to explain to the person
 * adopting; with the factors, you can show <em>why</em> that animal came out on
 * top -- and you can debug it when it comes out in the wrong place.
 *
 * @param score     sum of the factor points
 * @param blocked   whether a blocking factor is present
 * @param factors   what added or subtracted, and how much
 * @param blockers  reasons for elimination, when there are any
 */
public record CompatibilityResult(
		int score,
		boolean blocked,
		List<Factor> factors,
		List<String> blockers) {

	/**
	 * @param category stable label for the factor (e.g. {@code SPECIES})
	 * @param points   contribution to the score
	 * @param detail   human-readable explanation
	 */
	public record Factor(String category, int points, String detail) {
	}

	public CompatibilityResult {
		factors = List.copyOf(factors);
		blockers = List.copyOf(blockers);
	}
}
