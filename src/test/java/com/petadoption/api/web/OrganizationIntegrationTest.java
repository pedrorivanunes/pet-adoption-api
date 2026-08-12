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

class OrganizationIntegrationTest extends AbstractIntegrationTest {

	@Test
	@DisplayName("quem cria a organização já nasce como ADMIN dela")
	void creatorBecomesAdmin() throws Exception {
		String token = tokenFor("fundadora@example.com");
		long orgId = createOrganization(token, "Patas Felizes");

		mockMvc.perform(get("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].email").value("fundadora@example.com"))
				.andExpect(jsonPath("$[0].role").value("ADMIN"));
	}

	@Test
	@DisplayName("listagem de organizações é pública; criar exige token")
	void listingIsPublicButCreatingIsNot() throws Exception {
		mockMvc.perform(get("/api/organizations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray());

		mockMvc.perform(post("/api/organizations")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("name", "Sem Dono"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("quem não tem vínculo não vê o quadro de membros nem edita a organização")
	void outsiderIsBlocked() throws Exception {
		String admin = tokenFor("dona-org@example.com");
		long orgId = createOrganization(admin, "Abrigo Central");
		String outsider = tokenFor("curiosa@example.com");

		mockMvc.perform(get("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(outsider)))
				.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/organizations/" + orgId)
						.header("Authorization", bearer(outsider))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("name", "Tomada de Assalto"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(delete("/api/organizations/" + orgId)
						.header("Authorization", bearer(outsider)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("STAFF cuida dos pets, mas não mexe no quadro de membros")
	void staffCannotManageMembers() throws Exception {
		String admin = tokenFor("admin-membros@example.com");
		long orgId = createOrganization(admin, "Abrigo Membros");
		String staff = tokenFor("staff-membros@example.com");
		addMember(admin, orgId, "staff-membros@example.com", "STAFF");

		// enxerga o quadro, porque tem vínculo
		mockMvc.perform(get("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(staff)))
				.andExpect(status().isOk());

		// mas não pode alterá-lo
		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(staff))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "admin-membros@example.com", "role", "STAFF"))))
				.andExpect(status().isForbidden());

		// nem a organização em si
		mockMvc.perform(put("/api/organizations/" + orgId)
						.header("Authorization", bearer(staff))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("name", "Renomeada pelo Staff"))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("vincular duas vezes a mesma pessoa devolve 409")
	void duplicateMembershipIsRejected() throws Exception {
		String admin = tokenFor("admin-dup@example.com");
		long orgId = createOrganization(admin, "Abrigo Duplicado");
		tokenFor("membro-dup@example.com");

		addMember(admin, orgId, "membro-dup@example.com", "STAFF");

		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "membro-dup@example.com", "role", "ADMIN"))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("a organização não pode ficar sem administrador")
	void lastAdminCannotLeaveOrBeDemoted() throws Exception {
		String admin = tokenFor("unica-admin@example.com");
		long orgId = createOrganization(admin, "Abrigo Solo");
		long adminId = userIdOf(admin);

		// rebaixar a si mesma deixaria a organização sem ninguém no comando
		mockMvc.perform(put("/api/organizations/" + orgId + "/members/" + adminId)
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("role", "STAFF"))))
				.andExpect(status().isConflict());

		// remover-se, idem
		mockMvc.perform(delete("/api/organizations/" + orgId + "/members/" + adminId)
						.header("Authorization", bearer(admin)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("com outro ADMIN no quadro, a saída do primeiro é permitida")
	void adminCanLeaveOnceThereIsAnother() throws Exception {
		String first = tokenFor("primeira-admin@example.com");
		long orgId = createOrganization(first, "Abrigo Dupla");
		long firstId = userIdOf(first);

		tokenFor("segunda-admin@example.com");
		addMember(first, orgId, "segunda-admin@example.com", "ADMIN");

		mockMvc.perform(delete("/api/organizations/" + orgId + "/members/" + firstId)
						.header("Authorization", bearer(first)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("ADMIN promove STAFF e depois o remove")
	void adminPromotesAndRemovesMember() throws Exception {
		String admin = tokenFor("gestora@example.com");
		long orgId = createOrganization(admin, "Abrigo Gestão");
		String member = tokenFor("promovido@example.com");
		long memberId = userIdOf(member);

		addMember(admin, orgId, "promovido@example.com", "STAFF");

		mockMvc.perform(put("/api/organizations/" + orgId + "/members/" + memberId)
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("role", "ADMIN"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"));

		mockMvc.perform(delete("/api/organizations/" + orgId + "/members/" + memberId)
						.header("Authorization", bearer(admin)))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("vincular pessoa não cadastrada devolve 404")
	void addingUnknownPersonReturnsNotFound() throws Exception {
		String admin = tokenFor("admin-404@example.com");
		long orgId = createOrganization(admin, "Abrigo Fantasma");

		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "ninguem@example.com", "role", "STAFF"))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("papel de membro fora do domínio é rejeitado com 400")
	void invalidRoleIsRejected() throws Exception {
		String admin = tokenFor("admin-papel@example.com");
		long orgId = createOrganization(admin, "Abrigo Papel");
		tokenFor("alvo-papel@example.com");

		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "alvo-papel@example.com", "role", "PRESIDENTE"))))
				.andExpect(status().isBadRequest());
	}

	// ----------------------------------------------------------------- apoio --

	private void addMember(String adminToken, long orgId, String email, String role) throws Exception {
		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", email, "role", role))))
				.andExpect(status().isCreated());
	}
}
