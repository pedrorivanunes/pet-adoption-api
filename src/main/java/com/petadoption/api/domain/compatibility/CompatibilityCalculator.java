package com.petadoption.api.domain.compatibility;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.Pet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Calcula o grau de compatibilidade entre um pet e um adotante.
 *
 * <p>Não depende de banco, de HTTP nem de nada do Spring além da anotação que a
 * torna injetável — dá para instanciar com {@code new} e testar por tabela de
 * casos. Regra de negócio densa como esta é onde erro dói mais e onde teste
 * unitário vale mais.
 *
 * <h2>Duas naturezas diferentes</h2>
 * <p><strong>Pontos</strong> medem afinidade: quanto mais alto, melhor o par.
 * <strong>Fatores impeditivos</strong> não são pontuação baixa, são eliminação
 * — dizem respeito ao bem-estar do animal, e nenhuma soma de pontos os
 * compensa. Por isso são devolvidos num campo separado, e não como um valor
 * muito negativo.
 *
 * <h2>Ausência de preferência</h2>
 * <p>Preferência não declarada não pontua nem penaliza. Quem não disse que
 * queria um cão não deve ser penalizado por receber um gato — a pessoa apenas
 * não opinou.
 */
@Component
public class CompatibilityCalculator {

	// Pesos da tabela de referência do domínio. Constantes nomeadas porque
	// número solto no meio da regra não se discute em revisão de código.
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

	// =================================================== características =====

	private void scoreSpecies(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		if (profile.getPreferredSpecies() == null) {
			return;
		}
		boolean match = profile.getPreferredSpecies().equalsIgnoreCase(pet.getSpecies());
		factors.add(new CompatibilityResult.Factor("SPECIES", match ? SPECIES_MATCH : -SPECIES_MATCH,
				match ? "Espécie é a desejada" : "Espécie diferente da desejada"));
	}

	private void scoreBreed(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		// Sem raça declarada no pet (comum em resgatados sem definição), não há
		// o que comparar: silêncio não é discordância.
		if (profile.getPreferredBreed() == null || pet.getBreed() == null) {
			return;
		}
		boolean match = profile.getPreferredBreed().equalsIgnoreCase(pet.getBreed());
		factors.add(new CompatibilityResult.Factor("BREED", match ? BREED_MATCH : -BREED_MATCH,
				match ? "Raça é a desejada" : "Raça diferente da desejada"));
	}

	private void scoreSize(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		if (profile.getPreferredSize() == null || pet.getSize() == null) {
			return;
		}
		boolean match = profile.getPreferredSize() == pet.getSize();
		factors.add(new CompatibilityResult.Factor("SIZE", match ? SIZE_MATCH : -SIZE_MATCH,
				match ? "Porte é o desejado" : "Porte diferente do desejado"));
	}

	private void scoreSex(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		if (profile.getPreferredSex() == null || pet.getSex() == null) {
			return;
		}
		boolean match = profile.getPreferredSex() == pet.getSex();
		factors.add(new CompatibilityResult.Factor("SEX", match ? SEX_MATCH : -SEX_MATCH,
				match ? "Sexo é o desejado" : "Sexo diferente do desejado"));
	}

	// =============================================================== saúde ===

	private void scoreHealth(Pet pet, AdopterProfile profile, List<CompatibilityResult.Factor> factors) {
		boolean petNeedsExtraCare = pet.isHasSpecialNeeds() || pet.isHasContinuousTreatment();
		boolean adopterAcceptsExtraCare = profile.isAcceptsSpecialNeeds() || profile.isAcceptsContinuousTreatment();

		if (petNeedsExtraCare) {
			// Recusar necessidades especiais é preferência declarada, não risco
			// ao animal: penaliza o par, mas não o elimina. Quem elimina são os
			// fatores sociais, que dizem respeito ao bem-estar.
			factors.add(new CompatibilityResult.Factor("HEALTH_SPECIAL_NEEDS",
					adopterAcceptsExtraCare ? HEALTH_NEEDS_ACCEPTED : HEALTH_NEEDS_DECLINED,
					adopterAcceptsExtraCare
							? "Animal exige cuidados especiais e o adotante aceita"
							: "Animal exige cuidados especiais e o adotante não aceita"));
		}
		else {
			factors.add(new CompatibilityResult.Factor("HEALTH_SPECIAL_NEEDS",
					adopterAcceptsExtraCare ? HEALTH_HEALTHY_OPEN_ADOPTER : HEALTH_HEALTHY_STRICT_ADOPTER,
					"Animal sem necessidades especiais nem tratamento contínuo"));
		}

		if (pet.isHasChronicDisease()) {
			factors.add(new CompatibilityResult.Factor("HEALTH_CHRONIC",
					profile.isAcceptsChronicDisease() ? HEALTH_NEEDS_ACCEPTED : HEALTH_NEEDS_DECLINED,
					profile.isAcceptsChronicDisease()
							? "Animal tem doença crônica e o adotante aceita"
							: "Animal tem doença crônica e o adotante não aceita"));
		}
		else if (!profile.isAcceptsChronicDisease()) {
			factors.add(new CompatibilityResult.Factor("HEALTH_CHRONIC", HEALTH_NO_CHRONIC_STRICT_ADOPTER,
					"Animal sem doença crônica, como o adotante procura"));
		}
	}

	// ============================================================== social ===

	private void evaluateSocial(Pet pet, AdopterProfile profile,
			List<CompatibilityResult.Factor> factors, List<String> blockers) {

		if (profile.isHasOtherPets()) {
			// null aqui significa "não se sabe", e desconhecimento não elimina
			// ninguém — só a negativa confirmada é impeditiva.
			if (Boolean.FALSE.equals(pet.getGoodWithOtherAnimals())) {
				blockers.add("O animal não se adapta à convivência com outros animais, "
						+ "e já há animais na casa do adotante.");
			}
			else if (Boolean.TRUE.equals(pet.getGoodWithOtherAnimals())) {
				factors.add(new CompatibilityResult.Factor("SOCIAL_OTHER_ANIMALS", SOCIAL_GETS_ALONG,
						"Animal convive bem com outros e o adotante já tem animais"));
			}
		}

		if (pet.isRequiresConstantCare()) {
			if (profile.isHasTimeAvailability()) {
				factors.add(new CompatibilityResult.Factor("SOCIAL_TIME", SOCIAL_HAS_TIME,
						"Animal exige cuidados constantes e o adotante tem disponibilidade"));
			}
			else {
				blockers.add("O animal exige cuidados constantes e o adotante não tem "
						+ "disponibilidade de tempo.");
			}
		}
	}
}
