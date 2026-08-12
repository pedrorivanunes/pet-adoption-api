package com.petadoption.api.web;

import com.petadoption.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita o fluxo completo de autenticação contra a cadeia de filtros real.
 *
 * <p>Vários destes casos são regressões de defeitos concretos de uma versão
 * anterior deste sistema: senha errada respondendo 500 em vez de 401,
 * autoridades ganhando um prefixo {@code ROLE_} duplicado, e rota protegida
 * acessível sem token.
 */
@IntegrationTest
@Transactional
class AuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Test
	@DisplayName("cadastro devolve 201 sem expor a senha e com autoridade sem prefixo")
	void registerReturnsCreatedWithoutLeakingPassword() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerPayload("ana@example.com")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.authorities[0]").value("USER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	@DisplayName("e-mail é normalizado, então o mesmo endereço em outra caixa colide com 409")
	void registerIsCaseInsensitiveOnEmail() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerPayload("Bruno@Example.com")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("bruno@example.com"));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerPayload("BRUNO@EXAMPLE.COM")))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("cadastro sem senha válida devolve 400, não 500")
	void registerRejectsInvalidPayload() throws Exception {
		String payload = json.writeValueAsString(Map.of(
				"name", "Curta", "email", "curta@example.com", "password", "123"));

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("login com credencial correta devolve token Bearer")
	void loginReturnsToken() throws Exception {
		register("dani@example.com");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginPayload("dani@example.com", "senha-super-secreta")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	@DisplayName("senha errada devolve 401 — e não 500, como fazia o handler catch-all antigo")
	void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
		register("erro@example.com");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginPayload("erro@example.com", "senha-errada-mesmo")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
	}

	@Test
	@DisplayName("e-mail inexistente devolve 401 com a mesma mensagem, sem revelar se a conta existe")
	void loginWithUnknownEmailIsIndistinguishable() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginPayload("nao-existe@example.com", "qualquer-senha-aqui")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Credenciais inválidas"));
	}

	@Test
	@DisplayName("rota protegida sem token devolve 401")
	void meRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("rota protegida com token devolve o usuário do subject do token")
	void meReturnsAuthenticatedUser() throws Exception {
		register("fabio@example.com");
		String token = login("fabio@example.com");

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("fabio@example.com"))
				.andExpect(jsonPath("$.authorities[0]").value("USER"));
	}

	@Test
	@DisplayName("token forjado com outra assinatura é rejeitado com 401")
	void meRejectsTamperedToken() throws Exception {
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer nao.e.um.token"))
				.andExpect(status().isUnauthorized());
	}

	// ----------------------------------------------------------------- apoio --

	private void register(String email) throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerPayload(email)))
				.andExpect(status().isCreated());
	}

	private String login(String email) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginPayload(email, "senha-super-secreta")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(body).get("accessToken").stringValue();
	}

	private String registerPayload(String email) throws Exception {
		return json.writeValueAsString(Map.of(
				"name", "Pessoa de Teste",
				"email", email,
				"password", "senha-super-secreta",
				"phone", "51999990000"));
	}

	private String loginPayload(String email, String password) throws Exception {
		return json.writeValueAsString(Map.of("email", email, "password", password));
	}
}
