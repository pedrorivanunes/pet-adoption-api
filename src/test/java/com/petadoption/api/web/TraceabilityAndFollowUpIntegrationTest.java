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

/** Linha do tempo do animal e acompanhamento pós-adoção. */
class TraceabilityAndFollowUpIntegrationTest extends AbstractIntegrationTest {

	// ================================================ rastreabilidade ========

	@Test
	@DisplayName("registrar resgate aceita data anterior ao cadastro do pet")
	void rescuePredatesRegistration() throws Exception {
		String tutor = tokenFor("resgatadora@example.com");
		long petId = createPet(tutor, "Sobrevivente");

		mockMvc.perform(post("/api/pets/" + petId + "/history")
						.header("Authorization", bearer(tutor))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "RESCUE", "location", "Avenida Ipiranga, Porto Alegre",
								"startedOn", LocalDate.now().minusYears(2).toString()))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind").value("RESCUE"))
				.andExpect(jsonPath("$.current").value(true))
				.andExpect(jsonPath("$.durationInDays").value(org.hamcrest.Matchers.greaterThan(700)));
	}

	@Test
	@DisplayName("nova permanência encerra a anterior, sem buraco na linha do tempo")
	void newStayClosesThePrevious() throws Exception {
		String tutor = tokenFor("linha-do-tempo@example.com");
		long petId = createPet(tutor, "Viajante");

		addStay(tutor, petId, "RESCUE", "Rua onde foi encontrado", LocalDate.now().minusMonths(6));
		addStay(tutor, petId, "FOSTER", "Lar temporário", LocalDate.now().minusMonths(2));

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
	@DisplayName("permanência que começa antes da atual é recusada")
	void staysCannotGoBackwards() throws Exception {
		String tutor = tokenFor("ordem@example.com");
		long petId = createPet(tutor, "Cronologia");
		addStay(tutor, petId, "SHELTER", "Abrigo", LocalDate.now().minusMonths(1));

		mockMvc.perform(post("/api/pets/" + petId + "/history")
						.header("Authorization", bearer(tutor))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "FOSTER", "location", "Antes",
								"startedOn", LocalDate.now().minusMonths(3).toString()))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("histórico é público, mas só quem administra escreve nele")
	void historyIsPublicButWriteIsNot() throws Exception {
		String tutor = tokenFor("dono-historico@example.com");
		long petId = createPet(tutor, "Publico");
		addStay(tutor, petId, "SHELTER", "Abrigo Municipal", LocalDate.now().minusDays(30));

		mockMvc.perform(get("/api/pets/" + petId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].location").value("Abrigo Municipal"));

		String estranho = tokenFor("estranho-historico@example.com");
		mockMvc.perform(post("/api/pets/" + petId + "/history")
						.header("Authorization", bearer(estranho))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "OTHER", "location", "Invenção",
								"startedOn", LocalDate.now().toString()))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("histórico público não revela a identidade de tutores pessoas físicas")
	void individualCustodiansAreNotIdentified() throws Exception {
		String tutor = tokenFor("anonima@example.com");
		long petId = createPet(tutor, "Discreto");
		addStay(tutor, petId, "FOSTER", "Casa da voluntária", LocalDate.now().minusDays(10));

		mockMvc.perform(get("/api/pets/" + petId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].custodian.type").value("USER"))
				.andExpect(jsonPath("$[0].custodian.name").doesNotExist());
	}

	// ======================================================== saúde ==========

	@Test
	@DisplayName("ficha de saúde é restrita a quem administra o animal")
	void healthRecordsAreRestricted() throws Exception {
		String tutor = tokenFor("dono-saude@example.com");
		long petId = createPet(tutor, "Paciente");

		mockMvc.perform(post("/api/pets/" + petId + "/health-records")
						.header("Authorization", bearer(tutor))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VACCINATION",
								"occurredOn", LocalDate.now().minusDays(5).toString(),
								"description", "V10, primeira dose"))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/pets/" + petId + "/health-records")
						.header("Authorization", bearer(tutor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].kind").value("VACCINATION"));

		mockMvc.perform(get("/api/pets/" + petId + "/health-records"))
				.andExpect(status().isUnauthorized());

		String estranho = tokenFor("estranho-saude@example.com");
		mockMvc.perform(get("/api/pets/" + petId + "/health-records")
						.header("Authorization", bearer(estranho)))
				.andExpect(status().isForbidden());
	}

	// ==================================================== pós-adoção =========

	@Test
	@DisplayName("aprovar transfere a tutoria e abre a permanência no lar adotivo")
	void approvalTransfersCustody() throws Exception {
		String abrigo = tokenFor("abrigo-transfere@example.com");
		long petId = createPet(abrigo, "Transferido");
		addStay(abrigo, petId, "SHELTER", "Abrigo de origem", LocalDate.now().minusMonths(3));

		String adotante = tokenFor("nova-tutora@example.com");
		saveAdopterProfile(adotante);
		approveAdoptionOf(petId, abrigo, adotante);

		// a tutoria passou para quem adotou
		mockMvc.perform(get("/api/pets/" + petId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ADOPTED"))
				.andExpect(jsonPath("$.owner.type").value("USER"))
				.andExpect(jsonPath("$.owner.name").value("nova-tutora"));

		// e a linha do tempo registrou a mudança sozinha
		mockMvc.perform(get("/api/pets/" + petId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].kind").value("SHELTER"))
				.andExpect(jsonPath("$[0].current").value(false))
				.andExpect(jsonPath("$[1].kind").value("ADOPTION"))
				.andExpect(jsonPath("$[1].current").value(true));
	}

	@Test
	@DisplayName("quem entregou o animal registra o acompanhamento; o adotante não")
	void onlyOriginRecordsFollowUp() throws Exception {
		String abrigo = tokenFor("abrigo-acompanha@example.com");
		long petId = createPet(abrigo, "Acompanhado");
		String adotante = tokenFor("adotante-acompanhado@example.com");
		saveAdopterProfile(adotante);
		approveAdoptionOf(petId, abrigo, adotante);

		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(abrigo))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VISIT", "occurredOn", LocalDate.now().toString(),
								"notes", "Animal bem adaptado"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind").value("VISIT"));

		// o adotante não atesta o próprio acompanhamento
		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(adotante))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VISIT", "occurredOn", LocalDate.now().toString()))))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("contato anterior à adoção é recusado")
	void followUpCannotPredateAdoption() throws Exception {
		String abrigo = tokenFor("abrigo-data@example.com");
		long petId = createPet(abrigo, "Datado");
		String adotante = tokenFor("adotante-data@example.com");
		saveAdopterProfile(adotante);
		approveAdoptionOf(petId, abrigo, adotante);

		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(abrigo))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "CALL",
								"occurredOn", LocalDate.now().minusDays(10).toString()))))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("relatório mostra a janela de seis meses e os meses sem contato")
	void reportShowsWindowAndGaps() throws Exception {
		String abrigo = tokenFor("abrigo-relatorio@example.com");
		long petId = createPet(abrigo, "Relatado");
		String adotante = tokenFor("adotante-relatorio@example.com");
		saveAdopterProfile(adotante);
		approveAdoptionOf(petId, abrigo, adotante);

		// sem nenhum contato, o mês corrente já aparece como lacuna
		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(abrigo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.window.minimumMonths").value(6))
				.andExpect(jsonPath("$.window.endsOn").value(LocalDate.now().plusMonths(6).toString()))
				.andExpect(jsonPath("$.window.complete").value(false))
				.andExpect(jsonPath("$.summary.interactionCount").value(0))
				.andExpect(jsonPath("$.summary.monthsWithoutContact[0]")
						.value(YearMonth.now().toString()));

		mockMvc.perform(post("/api/pets/" + petId + "/followups")
						.header("Authorization", bearer(abrigo))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("kind", "VISIT", "occurredOn", LocalDate.now().toString()))))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(abrigo)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.interactionCount").value(1))
				.andExpect(jsonPath("$.summary.monthsWithoutContact.length()").value(0));
	}

	@Test
	@DisplayName("relatório é visível ao adotante e fechado a terceiros")
	void reportVisibility() throws Exception {
		String abrigo = tokenFor("abrigo-visibilidade@example.com");
		long petId = createPet(abrigo, "Reservado");
		String adotante = tokenFor("adotante-visibilidade@example.com");
		saveAdopterProfile(adotante);
		approveAdoptionOf(petId, abrigo, adotante);

		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(adotante)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.adopter.email").value("adotante-visibilidade@example.com"));

		String estranho = tokenFor("curioso-relatorio@example.com");
		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(estranho)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("pet sem adoção não tem acompanhamento")
	void noAdoptionMeansNoFollowUp() throws Exception {
		String tutor = tokenFor("sem-adocao@example.com");
		long petId = createPet(tutor, "Disponivel");

		mockMvc.perform(get("/api/pets/" + petId + "/followup-report")
						.header("Authorization", bearer(tutor)))
				.andExpect(status().isNotFound());
	}

	// ----------------------------------------------------------------- apoio --

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

	/** Candidata o adotante e aprova, deixando a adoção registrada. */
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
