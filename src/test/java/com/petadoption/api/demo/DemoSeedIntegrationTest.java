package com.petadoption.api.demo;

import com.petadoption.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the demo scenario.
 *
 * <p>A sample database nobody verifies rots: a new rule breaks the seed, the
 * seed starts failing at boot, and it is only discovered when someone tries to
 * bring the project up to look at it. This test starts the application under the
 * {@code demo} profile and confirms the seeded world is what the documentation
 * promises.
 */
@IntegrationTest
@ActiveProfiles("demo")
class DemoSeedIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Test
	@DisplayName("the public catalogue comes already populated")
	void catalogIsSeeded() throws Exception {
		mockMvc.perform(get("/api/pets"))
				.andExpect(status().isOk())
				// Luna, Thor, Mia and Nina available; Bidu already adopted
				.andExpect(jsonPath("$.totalElements").value(4));

		mockMvc.perform(get("/api/pets?status=ADOPTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].name").value("Bidu"));
	}

	@Test
	@DisplayName("the sample accounts log in with the documented password")
	void demoAccountsLogIn() throws Exception {
		for (String email : new String[] { "ana@example.com", "carla@example.com", "diego@example.com" }) {
			login(email);
		}
	}

	@Test
	@DisplayName("the sample organization has an ADMIN and a STAFF")
	void organizationHasBothRoles() throws Exception {
		String ana = login("ana@example.com");

		mockMvc.perform(get("/api/organizations/1/members").header("Authorization", "Bearer " + ana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].role").value("ADMIN"))
				.andExpect(jsonPath("$[1].role").value("STAFF"));
	}

	@Test
	@DisplayName("the sample adopter has a pending application and a coherent ranking")
	void adopterHasPendingApplicationAndMatches() throws Exception {
		String carla = login("carla@example.com");

		mockMvc.perform(get("/api/adoptions/applications/me").header("Authorization", "Bearer " + carla))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"))
				.andExpect(jsonPath("$.content[0].pet.name").value("Luna"));

		// Carla's profile was designed to match Luna; Thor needs constant care
		// but she has time, so he is not eliminated -- he just scores lower. Mia
		// does not get along with other animals, but Carla has none, so she
		// shows up too.
		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", "Bearer " + carla))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].pet.name").value("Luna"))
				.andExpect(jsonPath("$.content[0].factors").isNotEmpty());
	}

	@Test
	@DisplayName("the sample adoption has a follow-up with a gap visible in the report")
	void adoptionHasFollowUpHistory() throws Exception {
		String ana = login("ana@example.com");

		String adopted = mockMvc.perform(get("/api/pets?status=ADOPTED"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long biduId = json.readTree(adopted).get("content").get(0).get("id").longValue();

		mockMvc.perform(get("/api/pets/" + biduId + "/followup-report")
						.header("Authorization", "Bearer " + ana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pet.name").value("Bidu"))
				.andExpect(jsonPath("$.adopter.email").value("diego@example.com"))
				.andExpect(jsonPath("$.summary.interactionCount").value(3))
				.andExpect(jsonPath("$.summary.healthRecordCount").value(1))
				// the middle months were left without contact on purpose, so the
				// report shows the gap working
				.andExpect(jsonPath("$.summary.monthsWithoutContact").isNotEmpty());
	}

	@Test
	@DisplayName("a shelter pet's timeline starts at the rescue")
	void traceabilityStartsAtRescue() throws Exception {
		String pets = mockMvc.perform(get("/api/pets?status=ADOPTED"))
				.andReturn().getResponse().getContentAsString();
		long biduId = json.readTree(pets).get("content").get(0).get("id").longValue();

		mockMvc.perform(get("/api/pets/" + biduId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].kind").value("RESCUE"))
				.andExpect(jsonPath("$[1].kind").value("SHELTER"))
				.andExpect(jsonPath("$[1].custodian.name").value("Four Paws Shelter"))
				.andExpect(jsonPath("$[2].kind").value("ADOPTION"))
				.andExpect(jsonPath("$[2].current").value(true));
	}

	private String login(String email) throws Exception {
		String response = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json.writeValueAsString(
								Map.of("email", email, "password", DemoDataSeeder.PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(response).get("accessToken").stringValue();
	}
}
