package com.petadoption.api.web;

import com.petadoption.api.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PetIntegrationTest extends AbstractIntegrationTest {

	@Test
	@DisplayName("pessoa autenticada cadastra pet em nome próprio")
	void createsOwnPet() throws Exception {
		String token = tokenFor("tutora@example.com");

		mockMvc.perform(post("/api/pets")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(petPayload("Luna"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Luna"))
				// espécie é normalizada: "dog" no pedido, "DOG" no catálogo
				.andExpect(jsonPath("$.species").value("DOG"))
				.andExpect(jsonPath("$.status").value("AVAILABLE"))
				.andExpect(jsonPath("$.owner.type").value("USER"));
	}

	@Test
	@DisplayName("catálogo é público: lista e detalhe abrem sem token")
	void catalogIsPublic() throws Exception {
		String token = tokenFor("catalogo@example.com");
		long petId = createPet(token, "Bidu");

		mockMvc.perform(get("/api/pets"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.totalElements").isNumber());

		mockMvc.perform(get("/api/pets/" + petId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Bidu"));
	}

	@Test
	@DisplayName("cadastrar pet exige token, mesmo o catálogo sendo público")
	void createRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/api/pets")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(petPayload("Anônimo"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a rota dos meus pets não é alcançável sem token")
	void myPetsRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/users/me/pets"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("meus pets lista apenas os meus")
	void myPetsListsOnlyMine() throws Exception {
		String mine = tokenFor("minha@example.com");
		String other = tokenFor("outra@example.com");
		createPet(mine, "Meu Pet");
		createPet(other, "Pet Alheio");

		mockMvc.perform(get("/api/users/me/pets").header("Authorization", bearer(mine)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].name").value("Meu Pet"));
	}

	@Test
	@DisplayName("quem não é dono não edita nem apaga o pet")
	void strangerCannotManageSomeoneElsesPet() throws Exception {
		String owner = tokenFor("dona@example.com");
		String stranger = tokenFor("estranho@example.com");
		long petId = createPet(owner, "Mel");

		mockMvc.perform(put("/api/pets/" + petId)
						.header("Authorization", bearer(stranger))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(petPayload("Sequestrada"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/pets/" + petId).header("Authorization", bearer(stranger)))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/pets/" + petId).header("Authorization", bearer(owner)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("cadastrar pet para organização sem ter vínculo é negado")
	void cannotCreatePetForOrganizationWithoutMembership() throws Exception {
		String admin = tokenFor("abrigo-admin@example.com");
		long orgId = createOrganization(admin, "Abrigo Esperança");

		String stranger = tokenFor("de-fora@example.com");
		Map<String, Object> payload = petPayload("Invasor");
		payload.put("ownerOrganizationId", orgId);

		mockMvc.perform(post("/api/pets")
						.header("Authorization", bearer(stranger))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(payload)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("STAFF da organização cadastra e edita pets dela — é o trabalho dele")
	void staffManagesOrganizationPets() throws Exception {
		String admin = tokenFor("admin-abrigo@example.com");
		long orgId = createOrganization(admin, "Abrigo Patas");

		String staff = tokenFor("staff-abrigo@example.com");
		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "staff-abrigo@example.com", "role", "STAFF"))))
				.andExpect(status().isCreated());

		Map<String, Object> payload = petPayload("Thor");
		payload.put("ownerOrganizationId", orgId);

		String created = mockMvc.perform(post("/api/pets")
						.header("Authorization", bearer(staff))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(payload)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.owner.type").value("ORGANIZATION"))
				.andExpect(jsonPath("$.owner.name").value("Abrigo Patas"))
				.andReturn().getResponse().getContentAsString();

		long petId = json.readTree(created).get("id").longValue();

		Map<String, Object> update = petPayload("Thor");
		update.put("status", "ADOPTED");

		mockMvc.perform(put("/api/pets/" + petId)
						.header("Authorization", bearer(staff))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(update)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ADOPTED"));
	}

	@Test
	@DisplayName("pet marcado como falecido não volta a outro estado")
	void deceasedIsTerminal() throws Exception {
		String token = tokenFor("luto@example.com");
		long petId = createPet(token, "Rex");

		Map<String, Object> deceased = petPayload("Rex");
		deceased.put("status", "DECEASED");
		mockMvc.perform(put("/api/pets/" + petId)
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(deceased)))
				.andExpect(status().isOk());

		Map<String, Object> revived = petPayload("Rex");
		revived.put("status", "AVAILABLE");
		mockMvc.perform(put("/api/pets/" + petId)
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(revived)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("pet inexistente devolve 404, não 500")
	void unknownPetReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/pets/999999"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("data de nascimento no futuro é rejeitada com 400")
	void rejectsFutureBirthDate() throws Exception {
		String token = tokenFor("futuro@example.com");
		Map<String, Object> payload = petPayload("Viajante");
		payload.put("birthDate", "3000-01-01");

		mockMvc.perform(post("/api/pets")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(payload)))
				.andExpect(status().isBadRequest());
	}

}
