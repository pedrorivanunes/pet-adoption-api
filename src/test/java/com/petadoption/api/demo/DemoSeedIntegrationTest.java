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
 * Verifica o cenário de demonstração.
 *
 * <p>Base de exemplo que ninguém confere apodrece: uma regra nova quebra o seed,
 * ele passa a falhar no boot, e só se descobre quando alguém tenta subir o
 * projeto para ver. Este teste sobe a aplicação com o perfil {@code demo} e
 * confirma que o mundo semeado é o que a documentação promete.
 */
@IntegrationTest
@ActiveProfiles("demo")
class DemoSeedIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Test
	@DisplayName("o catálogo público já vem povoado")
	void catalogIsSeeded() throws Exception {
		mockMvc.perform(get("/api/pets"))
				.andExpect(status().isOk())
				// Luna, Thor, Mia e Nina disponíveis; Bidu já foi adotado
				.andExpect(jsonPath("$.totalElements").value(4));

		mockMvc.perform(get("/api/pets?status=ADOPTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].name").value("Bidu"));
	}

	@Test
	@DisplayName("as contas de exemplo autenticam com a senha documentada")
	void demoAccountsLogIn() throws Exception {
		for (String email : new String[] { "ana@exemplo.br", "carla@exemplo.br", "diego@exemplo.br" }) {
			login(email);
		}
	}

	@Test
	@DisplayName("a organização de exemplo tem ADMIN e STAFF")
	void organizationHasBothRoles() throws Exception {
		String ana = login("ana@exemplo.br");

		mockMvc.perform(get("/api/organizations/1/members").header("Authorization", "Bearer " + ana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].role").value("ADMIN"))
				.andExpect(jsonPath("$[1].role").value("STAFF"));
	}

	@Test
	@DisplayName("a adotante de exemplo tem candidatura pendente e ranking coerente")
	void adopterHasPendingApplicationAndMatches() throws Exception {
		String carla = login("carla@exemplo.br");

		mockMvc.perform(get("/api/adoptions/applications/me").header("Authorization", "Bearer " + carla))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"))
				.andExpect(jsonPath("$.content[0].pet.name").value("Luna"));

		// O perfil da Carla foi desenhado para casar com a Luna; o Thor exige
		// cuidados constantes mas ela tem tempo, então não é eliminado — só
		// pontua menos. A Mia não convive com outros animais, mas Carla não tem
		// animais, então também aparece.
		mockMvc.perform(get("/api/adoptions/matches").header("Authorization", "Bearer " + carla))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].pet.name").value("Luna"))
				.andExpect(jsonPath("$.content[0].factors").isNotEmpty());
	}

	@Test
	@DisplayName("a adoção de exemplo tem acompanhamento com lacuna visível no relatório")
	void adoptionHasFollowUpHistory() throws Exception {
		String ana = login("ana@exemplo.br");

		String adopted = mockMvc.perform(get("/api/pets?status=ADOPTED"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long biduId = json.readTree(adopted).get("content").get(0).get("id").longValue();

		mockMvc.perform(get("/api/pets/" + biduId + "/followup-report")
						.header("Authorization", "Bearer " + ana))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pet.name").value("Bidu"))
				.andExpect(jsonPath("$.adopter.email").value("diego@exemplo.br"))
				.andExpect(jsonPath("$.summary.interactionCount").value(3))
				.andExpect(jsonPath("$.summary.healthRecordCount").value(1))
				// meses do meio ficaram sem contato de propósito, para o
				// relatório mostrar a lacuna funcionando
				.andExpect(jsonPath("$.summary.monthsWithoutContact").isNotEmpty());
	}

	@Test
	@DisplayName("a linha do tempo de um pet do abrigo começa no resgate")
	void traceabilityStartsAtRescue() throws Exception {
		String pets = mockMvc.perform(get("/api/pets?status=ADOPTED"))
				.andReturn().getResponse().getContentAsString();
		long biduId = json.readTree(pets).get("content").get(0).get("id").longValue();

		mockMvc.perform(get("/api/pets/" + biduId + "/history"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].kind").value("RESCUE"))
				.andExpect(jsonPath("$[1].kind").value("SHELTER"))
				.andExpect(jsonPath("$[1].custodian.name").value("Abrigo Quatro Patas"))
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
