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
	@DisplayName("whoever creates the organization is born its ADMIN")
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
	@DisplayName("listing organizations is public; creating one requires a token")
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
	@DisplayName("a non-member neither sees the member list nor edits the organization")
	void outsiderIsBlocked() throws Exception {
		String admin = tokenFor("dona-org@example.com");
		long orgId = createOrganization(admin, "Central Shelter");
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
	@DisplayName("STAFF cares for the pets but does not touch the member list")
	void staffCannotManageMembers() throws Exception {
		String admin = tokenFor("admin-membros@example.com");
		long orgId = createOrganization(admin, "Members Shelter");
		String staff = tokenFor("staff-membros@example.com");
		addMember(admin, orgId, "staff-membros@example.com", "STAFF");

		// sees the list, because they are a member
		mockMvc.perform(get("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(staff)))
				.andExpect(status().isOk());

		// but cannot change it
		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(staff))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "admin-membros@example.com", "role", "STAFF"))))
				.andExpect(status().isForbidden());

		// nor the organization itself
		mockMvc.perform(put("/api/organizations/" + orgId)
						.header("Authorization", bearer(staff))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("name", "Renamed By Staff"))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("linking the same person twice returns 409")
	void duplicateMembershipIsRejected() throws Exception {
		String admin = tokenFor("admin-dup@example.com");
		long orgId = createOrganization(admin, "Duplicate Shelter");
		tokenFor("membro-dup@example.com");

		addMember(admin, orgId, "membro-dup@example.com", "STAFF");

		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "membro-dup@example.com", "role", "ADMIN"))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("the organization cannot be left without an administrator")
	void lastAdminCannotLeaveOrBeDemoted() throws Exception {
		String admin = tokenFor("unica-admin@example.com");
		long orgId = createOrganization(admin, "Solo Shelter");
		long adminId = userIdOf(admin);

		// demoting themselves would leave the organization with nobody in charge
		mockMvc.perform(put("/api/organizations/" + orgId + "/members/" + adminId)
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("role", "STAFF"))))
				.andExpect(status().isConflict());

		// removing themselves, likewise
		mockMvc.perform(delete("/api/organizations/" + orgId + "/members/" + adminId)
						.header("Authorization", bearer(admin)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("with another ADMIN on the list, the first one may leave")
	void adminCanLeaveOnceThereIsAnother() throws Exception {
		String first = tokenFor("primeira-admin@example.com");
		long orgId = createOrganization(first, "Double Shelter");
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
		long orgId = createOrganization(admin, "Governance Shelter");
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
	@DisplayName("linking an unregistered person returns 404")
	void addingUnknownPersonReturnsNotFound() throws Exception {
		String admin = tokenFor("admin-404@example.com");
		long orgId = createOrganization(admin, "Ghost Shelter");

		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "ninguem@example.com", "role", "STAFF"))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("a member role outside the domain is rejected with 400")
	void invalidRoleIsRejected() throws Exception {
		String admin = tokenFor("admin-role@example.com");
		long orgId = createOrganization(admin, "Role Shelter");
		tokenFor("target-role@example.com");

		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", "target-role@example.com", "role", "PRESIDENT"))))
				.andExpect(status().isBadRequest());
	}

	// --------------------------------------------------------------- helpers --

	private void addMember(String adminToken, long orgId, String email, String role) throws Exception {
		mockMvc.perform(post("/api/organizations/" + orgId + "/members")
						.header("Authorization", bearer(adminToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", email, "role", role))))
				.andExpect(status().isCreated());
	}
}
