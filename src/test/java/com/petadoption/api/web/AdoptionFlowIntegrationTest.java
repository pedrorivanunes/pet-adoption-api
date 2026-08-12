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
 * Perfil de adotante e ciclo completo da candidatura.
 *
 * <p>Estas regras não têm vida fora do banco — unicidade de candidatura
 * pendente, transição de estado, efeito em cascata da aprovação — então são
 * testadas onde de fato moram, e não contra repositórios simulados.
 */
class AdoptionFlowIntegrationTest extends AbstractIntegrationTest {

	// ==================================================== perfil =============

	@Test
	@DisplayName("perfil ainda não preenchido devolve 404 com explicação")
	void missingProfileReturnsNotFound() throws Exception {
		String token = tokenFor("sem-perfil@example.com");

		mockMvc.perform(get("/api/users/me/adopter-profile").header("Authorization", bearer(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("PUT do perfil é idempotente: cria e depois atualiza")
	void profileIsUpsert() throws Exception {
		String token = tokenFor("perfil@example.com");

		mockMvc.perform(put("/api/users/me/adopter-profile")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("housingType", "APARTMENT", "hasOtherPets", true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housingType").value("APARTMENT"))
				.andExpect(jsonPath("$.hasOtherPets").value(true))
				// não informado assume "sim": o impeditivo por falta de tempo
				// só dispara com negativa explícita
				.andExpect(jsonPath("$.hasTimeAvailability").value(true));

		mockMvc.perform(put("/api/users/me/adopter-profile")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("housingType", "RURAL", "preferredSpecies", "cat"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housingType").value("RURAL"))
				.andExpect(jsonPath("$.hasOtherPets").value(false))
				// espécie preferida usa a mesma forma canônica do cadastro do pet
				.andExpect(jsonPath("$.preferredSpecies").value("CAT"));

		mockMvc.perform(get("/api/users/me/adopter-profile").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.housingType").value("RURAL"));
	}

	@Test
	@DisplayName("perfil sem tipo de moradia é rejeitado com 400")
	void profileRequiresHousingType() throws Exception {
		String token = tokenFor("perfil-invalido@example.com");

		mockMvc.perform(put("/api/users/me/adopter-profile")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("hasChildren", true))))
				.andExpect(status().isBadRequest());
	}

	// ============================================== candidatura ==============

	@Test
	@DisplayName("candidatar-se sem perfil preenchido é recusado")
	void applyingRequiresProfile() throws Exception {
		String tutor = tokenFor("tutor-a@example.com");
		long petId = createPet(tutor, "Nina");
		String adopter = tokenFor("candidata-sem-perfil@example.com");

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("não é possível se candidatar ao próprio pet")
	void cannotApplyToOwnPet() throws Exception {
		String tutor = tokenFor("tutor-b@example.com");
		saveAdopterProfile(tutor);
		long petId = createPet(tutor, "Bidu");

		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(tutor))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("candidatura válida nasce pendente")
	void createsPendingApplication() throws Exception {
		String tutor = tokenFor("tutor-c@example.com");
		long petId = createPet(tutor, "Mel");
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
	@DisplayName("um pet por vez: segunda candidatura pendente é recusada, e cancelar libera")
	void onlyOnePendingApplicationAtATime() throws Exception {
		String tutor = tokenFor("tutor-d@example.com");
		long primeiro = createPet(tutor, "Primeiro");
		long segundo = createPet(tutor, "Segundo");

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

		// com a vaga livre, a nova candidatura passa
		mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", segundo))))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("aprovar move o pet para adotado e recusa os demais candidatos")
	void approvingClosesTheCycle() throws Exception {
		String tutor = tokenFor("tutor-e@example.com");
		long petId = createPet(tutor, "Disputado");

		String escolhida = tokenFor("escolhida@example.com");
		saveAdopterProfile(escolhida);
		long aprovada = apply(escolhida, petId);

		String preterida = tokenFor("preterida@example.com");
		saveAdopterProfile(preterida);
		long recusada = apply(preterida, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + aprovada + "/approve")
						.header("Authorization", bearer(tutor))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("note", "Perfil combina muito"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.decidedAt").exists());

		// o pet sai do catálogo de disponíveis
		mockMvc.perform(get("/api/pets/" + petId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ADOPTED"));

		// e ninguém fica esperando resposta que não virá
		mockMvc.perform(get("/api/adoptions/applications/" + recusada)
						.header("Authorization", bearer(preterida)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.decisionNote").value("Outra candidatura foi aprovada para este pet."));
	}

	@Test
	@DisplayName("pet já adotado não recebe novas candidaturas")
	void adoptedPetAcceptsNoMoreApplications() throws Exception {
		String tutor = tokenFor("tutor-f@example.com");
		long petId = createPet(tutor, "Já Foi");

		String primeira = tokenFor("primeira-f@example.com");
		saveAdopterProfile(primeira);
		long applicationId = apply(primeira, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/approve")
						.header("Authorization", bearer(tutor)))
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
	@DisplayName("candidatura já decidida não é decidida de novo")
	void decidedApplicationIsFinal() throws Exception {
		String tutor = tokenFor("tutor-g@example.com");
		long petId = createPet(tutor, "Decidido");
		String adopter = tokenFor("candidata-g@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/reject")
						.header("Authorization", bearer(tutor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/approve")
						.header("Authorization", bearer(tutor)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("quem não administra o pet não decide nem enxerga as candidaturas dele")
	void onlyPetManagerDecides() throws Exception {
		String tutor = tokenFor("tutor-h@example.com");
		long petId = createPet(tutor, "Protegido");
		String adopter = tokenFor("candidata-h@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		String estranho = tokenFor("estranho-h@example.com");

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/approve")
						.header("Authorization", bearer(estranho)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/pets/" + petId + "/applications")
						.header("Authorization", bearer(estranho)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/pets/" + petId + "/applications")
						.header("Authorization", bearer(tutor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@DisplayName("só quem se candidatou pode desistir da candidatura")
	void onlyAdopterCancels() throws Exception {
		String tutor = tokenFor("tutor-i@example.com");
		long petId = createPet(tutor, "Cancelável");
		String adopter = tokenFor("candidata-i@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/cancel")
						.header("Authorization", bearer(tutor)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("candidatura alheia não é visível para terceiros")
	void applicationIsPrivateToThePartiesInvolved() throws Exception {
		String tutor = tokenFor("tutor-j@example.com");
		long petId = createPet(tutor, "Reservado");
		String adopter = tokenFor("candidata-j@example.com");
		saveAdopterProfile(adopter);
		long applicationId = apply(adopter, petId);

		String estranho = tokenFor("bisbilhoteiro@example.com");

		mockMvc.perform(get("/api/adoptions/applications/" + applicationId)
						.header("Authorization", bearer(estranho)))
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

	// ----------------------------------------------------------------- apoio --

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
