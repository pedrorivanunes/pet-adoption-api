package com.petadoption.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base para testes que precisam de usuários autenticados de verdade.
 *
 * <p>Os tokens são obtidos passando pelos endpoints reais de cadastro e login,
 * e não fabricados no teste: um token forjado no próprio teste validaria só a
 * fabricação, não a cadeia que a aplicação de fato usa.
 */
@IntegrationTest
@Transactional
public abstract class AbstractIntegrationTest {

	protected static final String PASSWORD = "senha-super-secreta";

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper json;

	/** Cadastra a pessoa e devolve um token de acesso válido para ela. */
	protected String tokenFor(String email) throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of(
								"name", email.substring(0, email.indexOf('@')),
								"email", email,
								"password", PASSWORD))))
				.andExpect(status().isCreated());

		String response = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(response).get("accessToken").stringValue();
	}

	protected long createOrganization(String token, String name) throws Exception {
		String response = mockMvc.perform(post("/api/organizations")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("name", name))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(response).get("id").longValue();
	}

	protected long userIdOf(String token) throws Exception {
		String response = mockMvc.perform(
						org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me")
								.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(response).get("id").longValue();
	}

	protected Map<String, Object> petPayload(String name) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", name);
		payload.put("species", "dog");
		payload.put("size", "MEDIUM");
		payload.put("sex", "FEMALE");
		return payload;
	}

	protected String body(Map<String, ?> content) {
		return json.writeValueAsString(content);
	}

	protected String bearer(String token) {
		return "Bearer " + token;
	}
}
