package com.petadoption.api.domain.compatibility;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.HousingType;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A table of cases for the compatibility calculation.
 *
 * <p>No Spring and no database: the calculator is a pure function, and that is
 * how it is exercised. A dense rule like this one changes often -- each case
 * here is a contract on what the change must not break.
 */
class CompatibilityCalculatorTest {

	private final CompatibilityCalculator calculator = new CompatibilityCalculator();

	@Nested
	@DisplayName("scoring by characteristic")
	class Characteristics {

		@Test
		@DisplayName("a perfect match adds up every positive factor")
		void perfectMatch() {
			Pet pet = healthyPet();
			pet.setSpecies("DOG");
			pet.setBreed("Vira-lata");
			pet.setSize(PetSize.MEDIUM);
			pet.setSex(PetSex.FEMALE);

			AdopterProfile profile = strictProfile();
			profile.setPreferredSpecies("DOG");
			profile.setPreferredBreed("Vira-lata");
			profile.setPreferredSize(PetSize.MEDIUM);
			profile.setPreferredSex(PetSex.FEMALE);

			// 20 species + 10 breed + 10 size + 5 sex + 10 healthy + 5 no chronic illness
			assertThat(calculator.evaluate(pet, profile).score()).isEqualTo(60);
		}

		@Test
		@DisplayName("a species other than the one wanted subtracts 20")
		void wrongSpeciesSubtracts() {
			Pet pet = healthyPet();
			pet.setSpecies("CAT");

			AdopterProfile profile = strictProfile();
			profile.setPreferredSpecies("DOG");

			// -20 species + 10 healthy + 5 no chronic illness
			assertThat(calculator.evaluate(pet, profile).score()).isEqualTo(-5);
		}

		@Test
		@DisplayName("an undeclared preference neither scores nor penalises")
		void silenceIsNotDisagreement() {
			Pet pet = healthyPet();
			pet.setSpecies("CAT");
			pet.setSize(PetSize.LARGE);
			pet.setSex(PetSex.MALE);

			// a profile with no stated preference at all
			AdopterProfile profile = strictProfile();

			CompatibilityResult result = calculator.evaluate(pet, profile);

			assertThat(result.factors())
					.extracting(CompatibilityResult.Factor::category)
					.doesNotContain("SPECIES", "SIZE", "SEX", "BREED");
			// only the two health factors remain
			assertThat(result.score()).isEqualTo(15);
		}

		@Test
		@DisplayName("a breed missing from the pet does not count against it")
		void unknownBreedIsNotPenalized() {
			Pet pet = healthyPet();
			pet.setBreed(null);

			AdopterProfile profile = strictProfile();
			profile.setPreferredBreed("Poodle");

			assertThat(calculator.evaluate(pet, profile).factors())
					.extracting(CompatibilityResult.Factor::category)
					.doesNotContain("BREED");
		}
	}

	@Nested
	@DisplayName("health factors")
	class Health {

		@Test
		@DisplayName("an animal with special needs and an adopter who accepts add 10")
		void specialNeedsAccepted() {
			Pet pet = healthyPet();
			pet.setHasSpecialNeeds(true);

			AdopterProfile profile = strictProfile();
			profile.setAcceptsSpecialNeeds(true);

			assertThat(pointsOf(calculator.evaluate(pet, profile), "HEALTH_SPECIAL_NEEDS")).isEqualTo(10);
		}

		@Test
		@DisplayName("an animal on continuous treatment and an adopter who declines subtract 10")
		void continuousTreatmentDeclined() {
			Pet pet = healthyPet();
			pet.setHasContinuousTreatment(true);

			AdopterProfile profile = strictProfile();

			assertThat(pointsOf(calculator.evaluate(pet, profile), "HEALTH_SPECIAL_NEEDS")).isEqualTo(-10);
		}

		@Test
		@DisplayName("a healthy animal is worth more to someone looking for exactly that")
		void healthyPetIsWorthMoreToStrictAdopter() {
			Pet pet = healthyPet();

			AdopterProfile openAdopter = strictProfile();
			openAdopter.setAcceptsSpecialNeeds(true);

			assertThat(pointsOf(calculator.evaluate(pet, strictProfile()), "HEALTH_SPECIAL_NEEDS")).isEqualTo(10);
			assertThat(pointsOf(calculator.evaluate(pet, openAdopter), "HEALTH_SPECIAL_NEEDS")).isEqualTo(5);
		}

		@Test
		@DisplayName("an accepted chronic illness adds 10; a declined one subtracts 10")
		void chronicDisease() {
			Pet sick = healthyPet();
			sick.setHasChronicDisease(true);

			AdopterProfile accepts = strictProfile();
			accepts.setAcceptsChronicDisease(true);

			assertThat(pointsOf(calculator.evaluate(sick, accepts), "HEALTH_CHRONIC")).isEqualTo(10);
			assertThat(pointsOf(calculator.evaluate(sick, strictProfile()), "HEALTH_CHRONIC")).isEqualTo(-10);
		}
	}

	@Nested
	@DisplayName("blocking factors")
	class BlockingFactors {

		@Test
		@DisplayName("an animal that does not get along eliminates someone who already has animals")
		void doesNotGetAlongWithOtherAnimals() {
			Pet pet = healthyPet();
			pet.setGoodWithOtherAnimals(false);

			AdopterProfile profile = strictProfile();
			profile.setHasOtherPets(true);

			CompatibilityResult result = calculator.evaluate(pet, profile);

			assertThat(result.blocked()).isTrue();
			assertThat(result.blockers()).singleElement().asString()
					.contains("does not get along with other animals");
		}

		@Test
		@DisplayName("unknown sociability eliminates nobody")
		void unknownSociabilityDoesNotBlock() {
			Pet pet = healthyPet();
			pet.setGoodWithOtherAnimals(null);

			AdopterProfile profile = strictProfile();
			profile.setHasOtherPets(true);

			assertThat(calculator.evaluate(pet, profile).blocked()).isFalse();
		}

		@Test
		@DisplayName("an unsociable animal is irrelevant to someone with no animals")
		void unsociablePetIsFineForAdopterWithoutPets() {
			Pet pet = healthyPet();
			pet.setGoodWithOtherAnimals(false);

			assertThat(calculator.evaluate(pet, strictProfile()).blocked()).isFalse();
		}

		@Test
		@DisplayName("constant care with no time available eliminates")
		void constantCareWithoutTime() {
			Pet pet = healthyPet();
			pet.setRequiresConstantCare(true);

			AdopterProfile profile = strictProfile();
			profile.setHasTimeAvailability(false);

			CompatibilityResult result = calculator.evaluate(pet, profile);

			assertThat(result.blocked()).isTrue();
			assertThat(result.blockers()).singleElement().asString().contains("needs constant care");
		}

		@Test
		@DisplayName("constant care with time available adds 5")
		void constantCareWithTime() {
			Pet pet = healthyPet();
			pet.setRequiresConstantCare(true);

			CompatibilityResult result = calculator.evaluate(pet, strictProfile());

			assertThat(result.blocked()).isFalse();
			assertThat(pointsOf(result, "SOCIAL_TIME")).isEqualTo(5);
		}

		@Test
		@DisplayName("a blocker is not a low score: it eliminates even with a high score")
		void blockingIsNotAScore() {
			Pet pet = healthyPet();
			pet.setSpecies("DOG");
			pet.setSize(PetSize.MEDIUM);
			pet.setSex(PetSex.FEMALE);
			pet.setRequiresConstantCare(true);

			AdopterProfile profile = strictProfile();
			profile.setPreferredSpecies("DOG");
			profile.setPreferredSize(PetSize.MEDIUM);
			profile.setPreferredSex(PetSex.FEMALE);
			profile.setHasTimeAvailability(false);

			CompatibilityResult result = calculator.evaluate(pet, profile);

			assertThat(result.score()).isGreaterThan(0);
			assertThat(result.blocked()).isTrue();
		}

		@Test
		@DisplayName("os dois impeditivos podem ocorrer juntos")
		void bothBlockersAtOnce() {
			Pet pet = healthyPet();
			pet.setGoodWithOtherAnimals(false);
			pet.setRequiresConstantCare(true);

			AdopterProfile profile = strictProfile();
			profile.setHasOtherPets(true);
			profile.setHasTimeAvailability(false);

			assertThat(calculator.evaluate(pet, profile).blockers()).hasSize(2);
		}
	}

	@Test
	@DisplayName("every factor comes with a readable explanation")
	void everyFactorIsExplainable() {
		Pet pet = healthyPet();
		pet.setSpecies("DOG");

		AdopterProfile profile = strictProfile();
		profile.setPreferredSpecies("CAT");

		assertThat(calculator.evaluate(pet, profile).factors())
				.isNotEmpty()
				.allSatisfy(factor -> {
					assertThat(factor.category()).isNotBlank();
					assertThat(factor.detail()).isNotBlank();
				});
	}

	// --------------------------------------------------------------- helpers --

	/** An animal with no health condition and no care requirement. */
	private static Pet healthyPet() {
		Pet pet = new Pet();
		pet.setName("Test");
		pet.setSpecies("DOG");
		return pet;
	}

	/** An adopter who accepts no special condition and lives alone, with no pets. */
	private static AdopterProfile strictProfile() {
		AdopterProfile profile = new AdopterProfile();
		profile.setHousingType(HousingType.HOUSE);
		profile.setHasTimeAvailability(true);
		return profile;
	}

	private static int pointsOf(CompatibilityResult result, String category) {
		return result.factors().stream()
				.filter(factor -> factor.category().equals(category))
				.mapToInt(CompatibilityResult.Factor::points)
				.sum();
	}
}
