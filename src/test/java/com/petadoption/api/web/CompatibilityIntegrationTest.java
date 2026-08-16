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

/** "I want to adopt" and the compatibility report, end to end. */
class CompatibilityIntegrationTest extends AbstractIntegrationTest {

	@Test
	@DisplayName("searching for matches requires a token")
	void matchesRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/adoptions/matches"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("with no adopter profile there is nothing to compare")
	void matchesRequireProfile() throws Exception {
		String token = tokenFor("sem-perfil-match@example.com");

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(token)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("the pet matching the preferences comes first, with the maths shown")
	void ranksByCompatibility() throws Exception {
		String guardian = tokenFor("guardian-match@example.com");
		createPet(guardian, Map.of("name", "Match", "species", "dog",
				"size", "MEDIUM", "sex", "FEMALE"));
		createPet(guardian, Map.of("name", "No Match", "species", "cat",
				"size", "LARGE", "sex", "MALE"));

		String adopter = tokenFor("adopter-match@example.com");
		saveProfile(adopter, Map.of("housingType", "HOUSE", "preferredSpecies", "dog",
				"preferredSize", "MEDIUM", "preferredSex", "FEMALE"));

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(adopter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].pet.name").value("Match"))
				// 20 species + 10 size + 5 sex + 10 healthy + 5 no chronic illness
				.andExpect(jsonPath("$.content[0].score").value(50))
				.andExpect(jsonPath("$.content[1].pet.name").value("No Match"))
				// and the ranking explains itself: every factor carries a reason
				.andExpect(jsonPath("$.content[0].factors[0].category").value("SPECIES"))
				.andExpect(jsonPath("$.content[0].factors[0].points").value(20))
				.andExpect(jsonPath("$.content[0].factors[0].detail").isNotEmpty());
	}

	@Test
	@DisplayName("an animal with a blocker does not appear in the search")
	void blockedPetsAreExcluded() throws Exception {
		String guardian = tokenFor("guardian-impeditivo@example.com");
		createPet(guardian, Map.of("name", "Sociavel", "species", "dog",
				"goodWithOtherAnimals", true));
		createPet(guardian, Map.of("name", "Not Sociable", "species", "dog",
				"goodWithOtherAnimals", false));

		String adopter = tokenFor("ja-tem-animais@example.com");
		saveProfile(adopter, Map.of("housingType", "HOUSE", "hasOtherPets", true));

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(adopter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].pet.name").value("Sociavel"));
	}

	@Test
	@DisplayName("your own pets are not offered to you")
	void ownPetsAreNotOffered() throws Exception {
		String guardian = tokenFor("guardian-proprio@example.com");
		createPet(guardian, Map.of("name", "Meu Cao", "species", "dog"));
		saveProfile(guardian, Map.of("housingType", "HOUSE"));

		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", bearer(guardian)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	@DisplayName("the application stores the score from the moment it was made")
	void applicationRecordsScoreSnapshot() throws Exception {
		String guardian = tokenFor("guardian-snapshot@example.com");
		long petId = createPet(guardian, Map.of("name", "Alvo", "species", "dog", "size", "MEDIUM"));

		String adopter = tokenFor("adopter-snapshot@example.com");
		saveProfile(adopter, Map.of("housingType", "HOUSE", "preferredSpecies", "dog",
				"preferredSize", "MEDIUM"));

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isCreated())
				// 20 species + 10 size + 10 healthy + 5 no chronic illness
				.andExpect(jsonPath("$.compatibilityScore").value(45))
				.andExpect(jsonPath("$.hasBlockingFactor").value(false));
	}

	@Test
	@DisplayName("an application with a blocker is recorded and flagged, not refused")
	void blockingFactorIsFlaggedNotRefused() throws Exception {
		String guardian = tokenFor("guardian-flag@example.com");
		long petId = createPet(guardian, Map.of("name", "Exigente", "species", "dog",
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
	@DisplayName("the pet report ranks applications by compatibility")
	void petReportRanksApplicants() throws Exception {
		String guardian = tokenFor("guardian-report@example.com");
		long petId = createPet(guardian, Map.of("name", "Disputado", "species", "dog", "size", "SMALL"));

		String pouco = tokenFor("pouco-compativel@example.com");
		saveProfile(pouco, Map.of("housingType", "HOUSE", "preferredSpecies", "cat",
				"preferredSize", "LARGE"));
		applyTo(pouco, petId);

		String muito = tokenFor("muito-compativel@example.com");
		saveProfile(muito, Map.of("housingType", "HOUSE", "preferredSpecies", "dog",
				"preferredSize", "SMALL"));
		applyTo(muito, petId);

		mockMvc.perform(get("/api/pets/" + petId + "/applications")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[0].adopter.email").value("muito-compativel@example.com"))
				.andExpect(jsonPath("$.content[1].adopter.email").value("pouco-compativel@example.com"));
	}

	// --------------------------------------------------------------- helpers --

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
