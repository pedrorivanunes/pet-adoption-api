package com.petadoption.api.service;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.domain.User;
import com.petadoption.api.domain.compatibility.CompatibilityCalculator;
import com.petadoption.api.domain.compatibility.CompatibilityResult;
import com.petadoption.api.repository.PetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * The "I want to adopt" search: available pets ranked by how well they match the
 * person searching.
 */
@Service
public class CompatibilityService {

	private final PetRepository pets;
	private final UserService users;
	private final AdopterProfileService profiles;
	private final PetAccess petAccess;
	private final CompatibilityCalculator calculator;

	public CompatibilityService(PetRepository pets, UserService users, AdopterProfileService profiles,
			PetAccess petAccess, CompatibilityCalculator calculator) {
		this.pets = pets;
		this.users = users;
		this.profiles = profiles;
		this.petAccess = petAccess;
		this.calculator = calculator;
	}

	public record Match(Pet pet, CompatibilityResult compatibility) {
	}

	/**
	 * Ranks the available pets for the authenticated adopter.
	 *
	 * <p>Unlike the score stored on an application, the calculation here is
	 * live: the search has to reflect the catalogue and the preferences as they
	 * are now.
	 *
	 * <p><strong>On the strategy:</strong> available pets are loaded and scored
	 * in memory. A catalogue of animals up for adoption is small by nature --
	 * hundreds, not millions -- and keeping the rule in one place in Java is
	 * worth more than rewriting it in SQL to save time nobody will notice. If it
	 * ever grows enough to hurt, the answer is to materialise the score, not to
	 * scatter the rule.
	 */
	@Transactional(readOnly = true)
	public Page<Match> matchesFor(String actorEmail, Pageable pageable) {
		User actor = users.getByEmail(actorEmail);
		AdopterProfile profile = profiles.findOf(actor).orElseThrow(() -> new ConflictException(
				"Fill in your adopter profile to see the pets that match you best."));

		List<Match> ranked = pets.findByStatus(PetStatus.AVAILABLE, Pageable.unpaged()).getContent().stream()
				// No point offering someone an animal they already care for.
				.filter(pet -> !petAccess.canManage(pet, actor))
				.map(pet -> new Match(pet, calculator.evaluate(pet, profile)))
				// A blocker eliminates -- that is what it means. Showing up at
				// the bottom of the list would treat it as a low score.
				.filter(match -> !match.compatibility().blocked())
				.sorted(Comparator
						.comparingInt((Match match) -> match.compatibility().score()).reversed()
						// A stable tie-break, otherwise the same search returns
						// different orders and pagination repeats or skips rows.
						.thenComparing(match -> match.pet().getId()))
				.toList();

		return paginate(ranked, pageable);
	}

	private Page<Match> paginate(List<Match> ranked, Pageable pageable) {
		int from = (int) Math.min(pageable.getOffset(), ranked.size());
		int to = Math.min(from + pageable.getPageSize(), ranked.size());
		return new PageImpl<>(ranked.subList(from, to), pageable, ranked.size());
	}
}
