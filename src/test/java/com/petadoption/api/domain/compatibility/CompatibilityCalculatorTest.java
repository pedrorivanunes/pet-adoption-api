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
 * Tabela de casos do cálculo de compatibilidade.
 *
 * <p>Sem Spring e sem banco: o calculador é uma função pura, e é assim que ele
 * é exercitado. Regra densa como esta muda com frequência — cada caso aqui é um
 * contrato do que a mudança não pode quebrar.
 */
class CompatibilityCalculatorTest {

	private final CompatibilityCalculator calculator = new CompatibilityCalculator();

	@Nested
	@DisplayName("pontuação por característica")
	class Characteristics {

		@Test
		@DisplayName("par perfeito soma todos os fatores positivos")
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

			// 20 espécie + 10 raça + 10 porte + 5 sexo + 10 saudável + 5 sem crônica
			assertThat(calculator.evaluate(pet, profile).score()).isEqualTo(60);
		}

		@Test
		@DisplayName("espécie diferente da desejada subtrai 20")
		void wrongSpeciesSubtracts() {
			Pet pet = healthyPet();
			pet.setSpecies("CAT");

			AdopterProfile profile = strictProfile();
			profile.setPreferredSpecies("DOG");

			// -20 espécie + 10 saudável + 5 sem crônica
			assertThat(calculator.evaluate(pet, profile).score()).isEqualTo(-5);
		}

		@Test
		@DisplayName("preferência não declarada não pontua nem penaliza")
		void silenceIsNotDisagreement() {
			Pet pet = healthyPet();
			pet.setSpecies("CAT");
			pet.setSize(PetSize.LARGE);
			pet.setSex(PetSex.MALE);

			// perfil sem nenhuma preferência declarada
			AdopterProfile profile = strictProfile();

			CompatibilityResult result = calculator.evaluate(pet, profile);

			assertThat(result.factors())
					.extracting(CompatibilityResult.Factor::category)
					.doesNotContain("SPECIES", "SIZE", "SEX", "BREED");
			// sobram apenas os dois fatores de saúde
			assertThat(result.score()).isEqualTo(15);
		}

		@Test
		@DisplayName("raça não informada no pet não conta contra ele")
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
	@DisplayName("fatores de saúde")
	class Health {

		@Test
		@DisplayName("animal com necessidades especiais e adotante que aceita somam 10")
		void specialNeedsAccepted() {
			Pet pet = healthyPet();
			pet.setHasSpecialNeeds(true);

			AdopterProfile profile = strictProfile();
			profile.setAcceptsSpecialNeeds(true);

			assertThat(pointsOf(calculator.evaluate(pet, profile), "HEALTH_SPECIAL_NEEDS")).isEqualTo(10);
		}

		@Test
		@DisplayName("animal com tratamento contínuo e adotante que não aceita subtraem 10")
		void continuousTreatmentDeclined() {
			Pet pet = healthyPet();
			pet.setHasContinuousTreatment(true);

			AdopterProfile profile = strictProfile();

			assertThat(pointsOf(calculator.evaluate(pet, profile), "HEALTH_SPECIAL_NEEDS")).isEqualTo(-10);
		}

		@Test
		@DisplayName("animal saudável vale mais para quem procura exatamente isso")
		void healthyPetIsWorthMoreToStrictAdopter() {
			Pet pet = healthyPet();

			AdopterProfile openAdopter = strictProfile();
			openAdopter.setAcceptsSpecialNeeds(true);

			assertThat(pointsOf(calculator.evaluate(pet, strictProfile()), "HEALTH_SPECIAL_NEEDS")).isEqualTo(10);
			assertThat(pointsOf(calculator.evaluate(pet, openAdopter), "HEALTH_SPECIAL_NEEDS")).isEqualTo(5);
		}

		@Test
		@DisplayName("doença crônica aceita soma 10; recusada subtrai 10")
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
	@DisplayName("fatores impeditivos")
	class BlockingFactors {

		@Test
		@DisplayName("animal que não convive com outros elimina quem já tem animais")
		void doesNotGetAlongWithOtherAnimals() {
			Pet pet = healthyPet();
			pet.setGoodWithOtherAnimals(false);

			AdopterProfile profile = strictProfile();
			profile.setHasOtherPets(true);

			CompatibilityResult result = calculator.evaluate(pet, profile);

			assertThat(result.blocked()).isTrue();
			assertThat(result.blockers()).singleElement().asString()
					.contains("não se adapta à convivência");
		}

		@Test
		@DisplayName("convivência desconhecida não elimina ninguém")
		void unknownSociabilityDoesNotBlock() {
			Pet pet = healthyPet();
			pet.setGoodWithOtherAnimals(null);

			AdopterProfile profile = strictProfile();
			profile.setHasOtherPets(true);

			assertThat(calculator.evaluate(pet, profile).blocked()).isFalse();
		}

		@Test
		@DisplayName("animal que não convive é indiferente para quem não tem animais")
		void unsociablePetIsFineForAdopterWithoutPets() {
			Pet pet = healthyPet();
			pet.setGoodWithOtherAnimals(false);

			assertThat(calculator.evaluate(pet, strictProfile()).blocked()).isFalse();
		}

		@Test
		@DisplayName("cuidados constantes sem disponibilidade de tempo elimina")
		void constantCareWithoutTime() {
			Pet pet = healthyPet();
			pet.setRequiresConstantCare(true);

			AdopterProfile profile = strictProfile();
			profile.setHasTimeAvailability(false);

			CompatibilityResult result = calculator.evaluate(pet, profile);

			assertThat(result.blocked()).isTrue();
			assertThat(result.blockers()).singleElement().asString().contains("cuidados constantes");
		}

		@Test
		@DisplayName("cuidados constantes com disponibilidade soma 5")
		void constantCareWithTime() {
			Pet pet = healthyPet();
			pet.setRequiresConstantCare(true);

			CompatibilityResult result = calculator.evaluate(pet, strictProfile());

			assertThat(result.blocked()).isFalse();
			assertThat(pointsOf(result, "SOCIAL_TIME")).isEqualTo(5);
		}

		@Test
		@DisplayName("impeditivo não é pontuação baixa: elimina mesmo com score alto")
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
	@DisplayName("todo fator vem com explicação legível")
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

	// ----------------------------------------------------------------- apoio --

	/** Animal sem nenhuma condição de saúde e sem exigência de cuidado. */
	private static Pet healthyPet() {
		Pet pet = new Pet();
		pet.setName("Teste");
		pet.setSpecies("DOG");
		return pet;
	}

	/** Adotante que não aceita nenhuma condição especial e mora sozinho, sem pets. */
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
