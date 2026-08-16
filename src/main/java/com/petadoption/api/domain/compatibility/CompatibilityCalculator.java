package com.petadoption.api.domain.compatibility;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.Pet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes how well a pet and an adopter match.
 *
 * <p>It depends on no database, no HTTP and nothing from Spring beyond the
 * annotation that makes it injectable -- you can build it with {@code new} and
 * test it as a table of cases. Dense business rules like this one are where bugs
 * hurt most and where unit tests pay off most.
 *
 * <h2>Two different natures</h2>
 * <p><strong>Points</strong> measure affinity: the higher, the better the match.
 * <strong>Blockers</strong> are not a low score, they are elimination -- they
 * concern the animal's welfare, and no sum of points offsets them. That is why
 * they come back in a separate field instead of as a very negative number.
 *
 * <h2>Absent preferences</h2>
 * <p>A preference that was never stated neither scores nor penalises. Someone
 * who never said they wanted a dog should not be penalised for being offered a
 * cat -- they simply did not express an opinion.
 */
@Component
public class CompatibilityCalculator {

	// Weights from the domain reference table. Named constants, because a bare
	// number in the middle of a rule is not something a code review can discuss.
	private static final int SPECIES_MATCH = 20;
	private static final int BREED_MATCH = 10;
	private static final int SIZE_MATCH = 10;
	private static final int SEX_MATCH = 5;

	private static final int HEALTH_NEEDS_ACCEPTED = 10;
	private static final int HEALTH_NEEDS_DECLINED = -10;
	private static final int HEALTH_HEALTHY_OPEN_ADOPTER = 5;
	private static final int HEALTH_HEALTHY_STRICT_ADOPTER = 10;
	private static final int HEALTH_NO_CHRONIC_STRICT_ADOPTER = 5;

	private static final int SOCIAL_GETS_ALONG = 5;
	private static final int SOCIAL_HAS_TIME = 5;

	public CompatibilityResult evaluate(Pet pet, AdopterProfile profile) {
		List<CompatibilityResult.Factor> factors = new ArrayList<>();
		List<String> blockers = new ArrayList<>();

		scoreSpecies(pet, profile, factors);
		scoreBreed(pet, profile, factors);
		scoreSize(pet, profile, factors);
		scoreSex(pet, profile, factors);
		scoreHealth(pet, profile, factors);
		evaluateSocial(pet, profile, factors, blockers);

		int score = factors.stream().mapToInt(CompatibilityResult.Factor::points).sum();
		return new CompatibilityResult(score, !blockers.isEmpty(), factors, blockers);
	}

	// ======================================================= characteristics ==

	private void scoreSpecies(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		if (profile.getPreferredSpecies() == null) {
			return;
		}
		boolean match = profile.getPreferredSpecies().equalsIgnoreCase(pet.getSpecies());
		factors.add(new CompatibilityResult.Factor("SPECIES", match ? SPECIES_MATCH : -SPECIES_MATCH,
				match ? "Species is the one wanted" : "Species differs from the one wanted"));
	}

	private void scoreBreed(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		// With no breed recorded on the pet (common for mixed-breed rescues),
		// there is nothing to compare: silence is not disagreement.
		if (profile.getPreferredBreed() == null || pet.getBreed() == null) {
			return;
		}
		boolean match = profile.getPreferredBreed().equalsIgnoreCase(pet.getBreed());
		factors.add(new CompatibilityResult.Factor("BREED", match ? BREED_MATCH : -BREED_MATCH,
				match ? "Breed is the one wanted" : "Breed differs from the one wanted"));
	}

	private void scoreSize(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		if (profile.getPreferredSize() == null || pet.getSize() == null) {
			return;
		}
		boolean match = profile.getPreferredSize() == pet.getSize();
		factors.add(new CompatibilityResult.Factor("SIZE", match ? SIZE_MATCH : -SIZE_MATCH,
				match ? "Size is the one wanted" : "Size differs from the one wanted"));
	}

	private void scoreSex(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		if (profile.getPreferredSex() == null || pet.getSex() == null) {
			return;
		}
		boolean match = profile.getPreferredSex() == pet.getSex();
		factors.add(new CompatibilityResult.Factor("SEX", match ? SEX_MATCH : -SEX_MATCH,
				match ? "Sex is the one wanted" : "Sex differs from the one wanted"));
	}

	// ============================================================== health ===

	private void scoreHealth(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		boolean petNeedsExtraCare = pet.isHasSpecialNeeds() || pet.isHasContinuousTreatment();
		boolean adopterAcceptsExtraCare = profile.isAcceptsSpecialNeeds() || profile.isAcceptsContinuousTreatment();

		if (petNeedsExtraCare) {
			// Declining special needs is a stated preference, not a risk to the
			// animal: it penalises the match but does not eliminate it. What
			// eliminates are the social factors, which concern welfare.
			factors.add(new CompatibilityResult.Factor("HEALTH_SPECIAL_NEEDS",
					adopterAcceptsExtraCare ? HEALTH_NEEDS_ACCEPTED : HEALTH_NEEDS_DECLINED,
					adopterAcceptsExtraCare
							? "Animal needs special care and the adopter accepts it"
							: "Animal needs special care and the adopter does not accept it"));
		}
		else {
			factors.add(new CompatibilityResult.Factor("HEALTH_SPECIAL_NEEDS",
					adopterAcceptsExtraCare ? HEALTH_HEALTHY_OPEN_ADOPTER : HEALTH_HEALTHY_STRICT_ADOPTER,
					"Animal has no special needs and no continuous treatment"));
		}

		if (pet.isHasChronicDisease()) {
			factors.add(new CompatibilityResult.Factor("HEALTH_CHRONIC",
					profile.isAcceptsChronicDisease() ? HEALTH_NEEDS_ACCEPTED : HEALTH_NEEDS_DECLINED,
					profile.isAcceptsChronicDisease()
							? "Animal has a chronic illness and the adopter accepts it"
							: "Animal has a chronic illness and the adopter does not accept it"));
		}
		else if (!profile.isAcceptsChronicDisease()) {
			factors.add(new CompatibilityResult.Factor("HEALTH_CHRONIC", HEALTH_NO_CHRONIC_STRICT_ADOPTER,
					"Animal has no chronic illness, which is what the adopter wants"));
		}
	}

	// ============================================================== social ===

	private void evaluateSocial(Pet pet, AdopterProfile profile,
			List<CompatibilityResult.Factor> factors, List<String> blockers) {

		if (profile.isHasOtherPets()) {
			// null here means "unknown", and not knowing eliminates nobody --
			// only a confirmed negative is a blocker.
			if (Boolean.FALSE.equals(pet.getGoodWithOtherAnimals())) {
				blockers.add("The animal does not get along with other animals, "
						+ "and there are already animals in the adopter's home.");
			}
			else if (Boolean.TRUE.equals(pet.getGoodWithOtherAnimals())) {
				factors.add(new CompatibilityResult.Factor("SOCIAL_OTHER_ANIMALS", SOCIAL_GETS_ALONG,
						"Animal gets along with others and the adopter already has animals"));
			}
		}

		if (pet.isRequiresConstantCare()) {
			if (profile.isHasTimeAvailability()) {
				factors.add(new CompatibilityResult.Factor("SOCIAL_TIME", SOCIAL_HAS_TIME,
						"Animal needs constant care and the adopter has time available"));
			}
			else {
				blockers.add("The animal needs constant care and the adopter has no "
						+ "time available.");
			}
		}
	}
}
