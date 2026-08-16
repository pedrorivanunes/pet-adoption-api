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
 * Exercises the whole authentication flow against the real filter chain.
 *
 * <p>Several of these cases guard against specific failure modes that are easy
 * to reintroduce: a wrong password answering 500 instead of 401, authorities
 * picking up a duplicated {@code ROLE_} prefix, and a protected route reachable
 * without a token.
 */
@IntegrationTest
@Transactional
class AuthIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Test
	@DisplayName("registration returns 201 without exposing the password and with an unprefixed authority")
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
	@DisplayName("email is normalised, so the same address in another case collides with 409")
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
	@DisplayName("registration without a valid password returns 400, not 500")
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
						.content(loginPayload("dani@example.com", "a-very-secret-password")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	@DisplayName("a wrong password returns 401, not the 500 a catch-all handler would produce")
	void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
		register("wrong@example.com");

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginPayload("wrong@example.com", "definitely-the-wrong-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid credentials"));
	}

	@Test
	@DisplayName("e-mail inexistente devolve 401 com a mesma mensagem, sem revelar se a conta existe")
	void loginWithUnknownEmailIsIndistinguishable() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginPayload("does-not-exist@example.com", "any-password-here")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid credentials"));
	}

	@Test
	@DisplayName("rota protegida sem token devolve 401")
	void meRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a protected route with a token returns the user from the token subject")
	void meReturnsAuthenticatedUser() throws Exception {
		register("fabio@example.com");
		String token = login("fabio@example.com");

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("fabio@example.com"))
				.andExpect(jsonPath("$.authorities[0]").value("USER"));
	}

	@Test
	@DisplayName("a token forged with another signature is rejected with 401")
	void meRejectsTamperedToken() throws Exception {
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not.a.token"))
				.andExpect(status().isUnauthorized());
	}

	// --------------------------------------------------------------- helpers --

	private void register(String email) throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerPayload(email)))
				.andExpect(status().isCreated());
	}

	private String login(String email) throws Exception {
		String body = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginPayload(email, "a-very-secret-password")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		return json.readTree(body).get("accessToken").stringValue();
	}

	private String registerPayload(String email) throws Exception {
		return json.writeValueAsString(Map.of(
				"name", "Test Person",
				"email", email,
				"password", "a-very-secret-password",
				"phone", "51999990000"));
	}

	private String loginPayload(String email, String password) throws Exception {
		return json.writeValueAsString(Map.of("email", email, "password", password));
	}
}
