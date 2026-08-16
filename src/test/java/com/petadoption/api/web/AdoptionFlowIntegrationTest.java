package com.petadoption.api.web;

import com.petadoption.api.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The adopter profile and the full application cycle.
 *
 * <p>These rules have no life outside the database -- uniqueness of a pending
 * application, state transitions, the cascade effect of an approval -- so they
 * are tested where they actually live, and not against mocked repositories.
 */
class AdoptionFlowIntegrationTest extends AbstractIntegrationTest {

	// =================================================== profile =============

	@Test
	@DisplayName("a profile not yet filled in returns 404 with an explanation")
	void missingProfileReturnsNotFound() throws Exception {
		String token = tokenFor("sem-perfil@example.com");

		mockMvc.perform(get("/api/users/me/adopter-profile").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("PUT on the profile is idempotent: it creates and then updates")
	void profileIsUpsert() throws Exception {
		String token = tokenFor("perfil@example.com");

		mockMvc.perform(put("/api/users/me/adopter-profile")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("housingType", "APARTMENT", "hasOtherPets", true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housingType").value("APARTMENT"))
				.andExpect(jsonPath("$.hasOtherPets").value(true))
				// unset means "yes": the no-time blocker only fires on an
				// explicit negative
				.andExpect(jsonPath("$.hasTimeAvailability").value(true));

		mockMvc.perform(put("/api/users/me/adopter-profile")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("housingType", "RURAL", "preferredSpecies", "cat"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housingType").value("RURAL"))
				.andExpect(jsonPath("$.hasOtherPets").value(false))
				// the preferred species uses the same canonical form as the pet record
				.andExpect(jsonPath("$.preferredSpecies").value("CAT"));

		mockMvc.perform(get("/api/users/me/adopter-profile").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housingType").value("RURAL"));
	}

	@Test
	@DisplayName("a profile without a housing type is rejected with 400")
	void profileRequiresHousingType() throws Exception {
		String token = tokenFor("perfil-invalido@example.com");

		mockMvc.perform(put("/api/users/me/adopter-profile")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("hasChildren", true))))
				.andExpect(status().isBadRequest());
	}

	// ============================================= application ==============

	@Test
	@DisplayName("applying without a filled-in profile is refused")
	void applyingRequiresProfile() throws Exception {
		String guardian = tokenFor("guardian-a@example.com");
		long petId = createPet(guardian, "Nina");
		String adopter = tokenFor("candidata-sem-perfil@example.com");

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("you cannot apply for your own pet")
	void cannotApplyToOwnPet() throws Exception {
		String guardian = tokenFor("guardian-b@example.com");
		saveAdopterProfile(guardian);
		long petId = createPet(guardian, "Bidu");

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(guardian))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("a valid application is born pending")
	void createsPendingApplication() throws Exception {
		String guardian = tokenFor("guardian-c@example.com");
		long petId = createPet(guardian, "Mel");
		String adopter = tokenFor("candidata-c@example.com");
		saveAdopterProfile(adopter);

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId, "message", "Tenho quintal grande"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.pet.name").value("Mel"))
				.andExpect(jsonPath("$.adopter.email").value("candidata-c@example.com"))
				.andExpect(jsonPath("$.decidedAt").doesNotExist());
	}

	@Test
	@DisplayName("one pet at a time: a second pending application is refused, and cancelling frees the slot")
	void onlyOnePendingApplicationAtATime() throws Exception {
		String guardian = tokenFor("guardian-d@example.com");
		long primeiro = createPet(guardian, "Primeiro");
		long segundo = createPet(guardian, "Segundo");

		String adopter = tokenFor("indecisa@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, primeiro);

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", segundo))))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/cancel")
						.header("Authorization", bearer(adopter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELED"));

		// with the slot free, the new application goes through
		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", segundo))))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("approving moves the pet to adopted and rejects the other applicants")
	void approvingClosesTheCycle() throws Exception {
		String guardian = tokenFor("guardian-e@example.com");
		long petId = createPet(guardian, "Disputado");

		String escolhida = tokenFor("escolhida@example.com");
		saveAdopterProfile(escolhida);
		long approved = apply(escolhida, petId);

		String preterida = tokenFor("preterida@example.com");
		saveAdopterProfile(preterida);
		long recusada = apply(preterida, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + approved + "/approve")
						.header("Authorization", bearer(guardian))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("note", "The profile is a strong fit"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.decidedAt").exists());

		// the pet leaves the available catalogue
		mockMvc.perform(get("/api/pets/" + petId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ADOPTED"));

		// and nobody is left waiting for an answer that will not come
		mockMvc.perform(get("/api/adoptions/applications/" + recusada)
						.header("Authorization", bearer(preterida)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.decisionNote").value("Another application was approved for this pet."));
	}

	@Test
	@DisplayName("an already-adopted pet takes no new applications")
	void adoptedPetAcceptsNoMoreApplications() throws Exception {
		String guardian = tokenFor("guardian-f@example.com");
		long petId = createPet(guardian, "Already Gone");

		String primeira = tokenFor("primeira-f@example.com");
		saveAdopterProfile(primeira);
		long applicationId = apply(primeira, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/approve")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isOk());

		String atrasada = tokenFor("atrasada-f@example.com");
		saveAdopterProfile(atrasada);

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(atrasada))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("an already-decided application is not decided again")
	void decidedApplicationIsFinal() throws Exception {
		String guardian = tokenFor("guardian-g@example.com");
		long petId = createPet(guardian, "Decidido");
		String adopter = tokenFor("candidata-g@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/reject")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/approve")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("someone who does not manage the pet neither decides nor sees its applications")
	void onlyPetManagerDecides() throws Exception {
		String guardian = tokenFor("guardian-h@example.com");
		long petId = createPet(guardian, "Protegido");
		String adopter = tokenFor("candidata-h@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		String stranger = tokenFor("stranger-h@example.com");

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/approve")
						.header("Authorization", bearer(stranger)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/pets/" + petId + "/applications")
						.header("Authorization", bearer(stranger)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/pets/" + petId + "/applications")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@DisplayName("only the applicant can withdraw the application")
	void onlyAdopterCancels() throws Exception {
		String guardian = tokenFor("guardian-i@example.com");
		long petId = createPet(guardian, "Cancellable");
		String adopter = tokenFor("candidata-i@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/cancel")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("someone else's application is not visible to third parties")
	void applicationIsPrivateToThePartiesInvolved() throws Exception {
		String guardian = tokenFor("guardian-j@example.com");
		long petId = createPet(guardian, "Reserved");
		String adopter = tokenFor("candidata-j@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		String stranger = tokenFor("bisbilhoteiro@example.com");

		mockMvc.perform(get("/api/adoptions/applications/" + applicationId)
						.header("Authorization", bearer(stranger)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/adoptions/applications/" + applicationId)
						.header("Authorization", bearer(adopter)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("candidatar-se exige token")
	void applyingRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/adoptions/applications")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", 1))))
				.andExpect(status().isUnauthorized());
	}

	// --------------------------------------------------------------- helpers --

	private long apply(String adopterToken, long petId) throws Exception {
		String response = mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopterToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(response).get("id").longValue();
	}
}
