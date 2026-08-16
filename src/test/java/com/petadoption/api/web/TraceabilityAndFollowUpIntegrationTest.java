package com.petadoption.api.web;

import com.petadoption.api.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The animal's timeline and the post-adoption follow-up. */
class TraceabilityAndFollowUpIntegrationTest extends AbstractIntegrationTest {

	// =================================================== traceability ========

	@Test
	@DisplayName("recording a rescue accepts a date before the pet was registered")
	void rescuePredatesRegistration() throws Exception {
		String guardian = tokenFor("resgatadora@example.com");
		long petId = createPet(guardian, "Sobrevivente");

		mockMvc.perform(post("/api/pets/" + petId + "/history")
						.header("Authorization", bearer(guardian))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "RESCUE", "location", "Avenida Ipiranga, Porto Alegre",
								"startedOn", LocalDate.now().minusYears(2).toString()))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind").value("RESCUE"))
				.andExpect(jsonPath("$.current").value(true))
				.andExpect(jsonPath("$.durationInDays").value(org.hamcrest.Matchers.greaterThan(700)));
	}

	@Test
	@DisplayName("a new stay closes the previous one, with no gap in the timeline")
	void newStayClosesThePrevious() throws Exception {
		String guardian = tokenFor("linha-do-tempo@example.com");
		long petId = createPet(guardian, "Viajante");

		addStay(guardian, petId, "RESCUE", "Rua onde foi encontrado", LocalDate.now().minusMonths(6));
		addStay(guardian, petId, "FOSTER", "Foster home", LocalDate.now().minusMonths(2));

		mockMvc.perform(get("/api/pets/" + petId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].kind").value("RESCUE"))
				.andExpect(jsonPath("$[0].current").value(false))
				.andExpect(jsonPath("$[0].endedOn").value(LocalDate.now().minusMonths(2).toString()))
				.andExpect(jsonPath("$[1].kind").value("FOSTER"))
				.andExpect(jsonPath("$[1].current").value(true));
	}

	@Test
	@DisplayName("a stay starting before the current one is refused")
	void staysCannotGoBackwards() throws Exception {
		String guardian = tokenFor("ordem@example.com");
		long petId = createPet(guardian, "Cronologia");
		addStay(guardian, petId, "SHELTER", "Shelter", LocalDate.now().minusMonths(1));

		mockMvc.perform(post("/api/pets/" + petId + "/history")
						.header("Authorization", bearer(guardian))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "FOSTER", "location", "Antes",
								"startedOn", LocalDate.now().minusMonths(3).toString()))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("the history is public, but only a manager writes to it")
	void historyIsPublicButWriteIsNot() throws Exception {
		String guardian = tokenFor("dono-historico@example.com");
		long petId = createPet(guardian, "Publico");
		addStay(guardian, petId, "SHELTER", "Municipal Shelter", LocalDate.now().minusDays(30));

		mockMvc.perform(get("/api/pets/" + petId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].location").value("Municipal Shelter"));

		String stranger = tokenFor("stranger-historico@example.com");
		mockMvc.perform(post("/api/pets/" + petId + "/history")
						.header("Authorization", bearer(stranger))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "OTHER", "location", "Made up",
								"startedOn", LocalDate.now().toString()))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("the public history does not reveal the identity of individual guardians")
	void individualCustodiansAreNotIdentified() throws Exception {
		String guardian = tokenFor("anonima@example.com");
		long petId = createPet(guardian, "Discreto");
		addStay(guardian, petId, "FOSTER", "A volunteer's home", LocalDate.now().minusDays(10));

		mockMvc.perform(get("/api/pets/" + petId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].custodian.type").value("USER"))
				.andExpect(jsonPath("$[0].custodian.name").doesNotExist());
	}

	// ======================================================= health ==========

	@Test
	@DisplayName("the health record is restricted to whoever manages the animal")
	void healthRecordsAreRestricted() throws Exception {
		String guardian = tokenFor("owner-health@example.com");
		long petId = createPet(guardian, "Paciente");

		mockMvc.perform(post("/api/pets/" + petId + "/health-records")
						.header("Authorization", bearer(guardian))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VACCINATION",
								"occurredOn", LocalDate.now().minusDays(5).toString(),
								"description", "V10, primeira dose"))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/pets/" + petId + "/health-records")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].kind").value("VACCINATION"));

		mockMvc.perform(get("/api/pets/" + petId + "/health-records"))
				.andExpect(status().isUnauthorized());

		String stranger = tokenFor("stranger-health@example.com");
		mockMvc.perform(get("/api/pets/" + petId + "/health-records")
						.header("Authorization", bearer(stranger)))
				.andExpect(status().isForbidden());
	}

	// ================================================== post-adoption ========

	@Test
	@DisplayName("approving transfers guardianship and opens the stay in the adoptive home")
	void approvalTransfersCustody() throws Exception {
		String shelter = tokenFor("shelter-transfer@example.com");
		long petId = createPet(shelter, "Transferido");
		addStay(shelter, petId, "SHELTER", "Origin shelter", LocalDate.now().minusMonths(3));

		String adopter = tokenFor("nova-tutora@example.com");
		saveAdopterProfile(adopter);
		approveAdoptionOf(petId, shelter, adopter);

		// guardianship passed to whoever adopted
		mockMvc.perform(get("/api/pets/" + petId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ADOPTED"))
				.andExpect(jsonPath("$.owner.type").value("USER"))
				.andExpect(jsonPath("$.owner.name").value("nova-tutora"));

		// and the timeline recorded the change on its own
		mockMvc.perform(get("/api/pets/" + petId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].kind").value("SHELTER"))
				.andExpect(jsonPath("$[0].current").value(false))
				.andExpect(jsonPath("$[1].kind").value("ADOPTION"))
				.andExpect(jsonPath("$[1].current").value(true));
	}

	@Test
	@DisplayName("whoever handed the animal over logs the follow-up; the adopter does not")
	void onlyOriginRecordsFollowUp() throws Exception {
		String shelter = tokenFor("shelter-followup@example.com");
		long petId = createPet(shelter, "Acompanhado");
		String adopter = tokenFor("adopter-followed@example.com");
		saveAdopterProfile(adopter);
		approveAdoptionOf(petId, shelter, adopter);

		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(shelter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VISIT", "occurredOn", LocalDate.now().toString(),
								"notes", "Animal bem adaptado"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind").value("VISIT"));

		// the adopter does not certify their own follow-up
		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(adopter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VISIT", "occurredOn", LocalDate.now().toString()))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("a contact predating the adoption is refused")
	void followUpCannotPredateAdoption() throws Exception {
		String shelter = tokenFor("shelter-date@example.com");
		long petId = createPet(shelter, "Dated");
		String adopter = tokenFor("adopter-date@example.com");
		saveAdopterProfile(adopter);
		approveAdoptionOf(petId, shelter, adopter);

		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(shelter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "CALL",
								"occurredOn", LocalDate.now().minusDays(10).toString()))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("the report shows the six-month window and the months without contact")
	void reportShowsWindowAndGaps() throws Exception {
		String shelter = tokenFor("shelter-report@example.com");
		long petId = createPet(shelter, "Reported");
		String adopter = tokenFor("adopter-report@example.com");
		saveAdopterProfile(adopter);
		approveAdoptionOf(petId, shelter, adopter);

		// with no contact at all, the current month already shows as a gap
		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(shelter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.window.minimumMonths").value(6))
				.andExpect(jsonPath("$.window.endsOn").value(LocalDate.now().plusMonths(6).toString()))
				.andExpect(jsonPath("$.window.complete").value(false))
				.andExpect(jsonPath("$.summary.interactionCount").value(0))
				.andExpect(jsonPath("$.summary.monthsWithoutContact[0]")
						.value(YearMonth.now().toString()));

		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(shelter))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VISIT", "occurredOn", LocalDate.now().toString()))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(shelter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.interactionCount").value(1))
				.andExpect(jsonPath("$.summary.monthsWithoutContact.length()").value(0));
	}

	@Test
	@DisplayName("the report is visible to the adopter and closed to third parties")
	void reportVisibility() throws Exception {
		String shelter = tokenFor("shelter-visibility@example.com");
		long petId = createPet(shelter, "Reserved");
		String adopter = tokenFor("adopter-visibility@example.com");
		saveAdopterProfile(adopter);
		approveAdoptionOf(petId, shelter, adopter);

		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(adopter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.adopter.email").value("adopter-visibility@example.com"));

		String stranger = tokenFor("nosy-report@example.com");
		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(stranger)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("a pet with no adoption has no follow-up")
	void noAdoptionMeansNoFollowUp() throws Exception {
		String guardian = tokenFor("no-adoption@example.com");
		long petId = createPet(guardian, "Disponivel");

		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(guardian)))
				.andExpect(status().isNotFound());
	}

	// --------------------------------------------------------------- helpers --

	private void addStay(String token, long petId, String kind, String location, LocalDate startedOn)
			throws Exception {

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("kind", kind);
		payload.put("location", location);
		payload.put("startedOn", startedOn.toString());

		mockMvc.perform(post("/api/pets/" + petId + "/history")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(payload)))
				.andExpect(status().isCreated());
	}

	/** Applies as the adopter and approves, leaving the adoption on record. */
	private void approveAdoptionOf(long petId, String ownerToken, String adopterToken) throws Exception {
		String created = mockMvc.perform(post("/api/adoptions/applications")
						.header("Authorization", bearer(adopterToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("petId", petId))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		long applicationId = json.readTree(created).get("id").longValue();

		mockMvc.perform(post("/api/adoptions/applications/" + applicationId + "/approve")
						.header("Authorization", bearer(ownerToken)))
				.andExpect(status().isOk());
	}
}
