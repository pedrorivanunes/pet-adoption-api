package com.petadoption.api.web;

import com.petadoption.api.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "Quero adotar" e o relatório de compatibilidade, ponta a ponta. */
class CompatibilityIntegrationTest extends AbstractIntegrationTest {

	@Test
	@DisplayName("buscar compatíveis exige token")
	void matchesRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/adoptions/matches"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("sem perfil de adotante não há o que comparar")
	void matchesRequireProfile() throws Exception {
		String token = tokenFor("sem-perfil-match@example.com");

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(token)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("o pet que casa com as preferências vem na frente, com a conta aberta")
	void ranksByCompatibility() throws Exception {
		String tutor = tokenFor("tutor-match@example.com");
		createPet(tutor, Map.of("name", "Combina", "species", "dog",
				"size", "MEDIUM", "sex", "FEMALE"));
		createPet(tutor, Map.of("name", "Nao Combina", "species", "cat",
				"size", "LARGE", "sex", "MALE"));

		String adopter = tokenFor("adotante-match@example.com");
		saveProfile(adopter, Map.of("housingType", "HOUSE", "preferredSpecies", "dog",
				"preferredSize", "MEDIUM", "preferredSex", "FEMALE"));

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(adopter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].pet.name").value("Combina"))
				// 20 espécie + 10 porte + 5 sexo + 10 saudável + 5 sem crônica
				.andExpect(jsonPath("$.content[0].score").value(50))
				.andExpect(jsonPath("$.content[1].pet.name").value("Nao Combina"))
				// e o ranking se explica: cada fator vem com motivo
				.andExpect(jsonPath("$.content[0].factors[0].category").value("SPECIES"))
				.andExpect(jsonPath("$.content[0].factors[0].points").value(20))
				.andExpect(jsonPath("$.content[0].factors[0].detail").isNotEmpty());
	}

	@Test
	@DisplayName("animal com fator impeditivo não aparece na busca")
	void blockedPetsAreExcluded() throws Exception {
		String tutor = tokenFor("tutor-impeditivo@example.com");
		createPet(tutor, Map.of("name", "Sociavel", "species", "dog",
				"goodWithOtherAnimals", true));
		createPet(tutor, Map.of("name", "Nao Convive", "species", "dog",
				"goodWithOtherAnimals", false));

		String adopter = tokenFor("ja-tem-animais@example.com");
		saveProfile(adopter, Map.of("housingType", "HOUSE", "hasOtherPets", true));

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(adopter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].pet.name").value("Sociavel"));
	}

	@Test
	@DisplayName("os próprios pets não são oferecidos a quem cuida deles")
	void ownPetsAreNotOffered() throws Exception {
		String tutor = tokenFor("tutor-proprio@example.com");
		createPet(tutor, Map.of("name", "Meu Cao", "species", "dog"));
		saveProfile(tutor, Map.of("housingType", "HOUSE"));

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(tutor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	@DisplayName("a candidatura guarda o score do momento em que foi feita")
	void applicationRecordsScoreSnapshot() throws Exception {
		String tutor = tokenFor("tutor-snapshot@example.com");
		long petId = createPet(tutor, Map.of("name", "Alvo", "species", "dog", "size", "MEDIUM"));

		String adopter = tokenFor("adotante-snapshot@example.com");
		saveProfile(adopter, Map.of("housingType", "HOUSE", "preferredSpecies", "dog",
				"preferredSize", "MEDIUM"));

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isCreated())
				// 20 espécie + 10 porte + 10 saudável + 5 sem crônica
				.andExpect(jsonPath("$.compatibilityScore").value(45))
				.andExpect(jsonPath("$.hasBlockingFactor").value(false));
	}

	@Test
	@DisplayName("candidatura com impeditivo é registrada e sinalizada, não recusada")
	void blockingFactorIsFlaggedNotRefused() throws Exception {
		String tutor = tokenFor("tutor-flag@example.com");
		long petId = createPet(tutor, Map.of("name", "Exigente", "species", "dog",
				"requiresConstantCare", true));

		String adopter = tokenFor("sem-tempo@example.com");
		saveProfile(adopter, Map.of("housingType", "APARTMENT", "hasTimeAvailability", false));

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.hasBlockingFactor").value(true));
	}

	@Test
	@DisplayName("relatório do pet ranqueia as candidaturas por compatibilidade")
	void petReportRanksApplicants() throws Exception {
		String tutor = tokenFor("tutor-relatorio@example.com");
		long petId = createPet(tutor, Map.of("name", "Disputado", "species", "dog", "size", "SMALL"));

		String pouco = tokenFor("pouco-compativel@example.com");
		saveProfile(pouco, Map.of("housingType", "HOUSE", "preferredSpecies", "cat",
				"preferredSize", "LARGE"));
		applyTo(pouco, petId);

		String muito = tokenFor("muito-compativel@example.com");
		saveProfile(muito, Map.of("housingType", "HOUSE", "preferredSpecies", "dog",
				"preferredSize", "SMALL"));
		applyTo(muito, petId);

		mockMvc.perform(get("/api/pets/" + petId + "/applications")
						.header("Authorization", bearer(tutor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].adopter.email").value("muito-compativel@example.com"))
				.andExpect(jsonPath("$.content[1].adopter.email").value("pouco-compativel@example.com"));
	}

	// ----------------------------------------------------------------- apoio --

	private long createPet(String token, Map<String, Object> attributes) throws Exception {
		Map<String, Object> payload = new LinkedHashMap<>(attributes);

		String response = mockMvc.perform(post("/api/pets")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(payload)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(response).get("id").longValue();
	}

	private void saveProfile(String token, Map<String, Object> attributes) throws Exception {
		mockMvc.perform(put("/api/users/me/adopter-profile")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(attributes)))
				.andExpect(status().isOk());
	}

	private void applyTo(String token, long petId) throws Exception {
		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isCreated());
	}
}
