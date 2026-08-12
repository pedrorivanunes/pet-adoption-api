package com.petadoption.api.service;

import com.petadoption.api.domain.Role;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.RoleRepository;
import com.petadoption.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras de cadastro em isolamento — sem Spring, sem banco. O que precisa de
 * infraestrutura de verdade é coberto pelos testes de integração.
 */
class UserServiceTest {

	private UserRepository users;
	private RoleRepository roles;
	private UserService service;

	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	@BeforeEach
	void setUp() {
		users = mock(UserRepository.class);
		roles = mock(RoleRepository.class);
		service = new UserService(users, roles, encoder);

		when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("cadastro guarda o e-mail normalizado e a senha com hash")
	void registerNormalizesEmailAndHashesPassword() {
		givenDefaultRoleExists();

		User created = service.register(new UserService.NewUser(
				"  Pedro  ", "  Pedro@Example.COM ", "senha-super-secreta", "51999990000"));

		assertThat(created.getName()).isEqualTo("Pedro");
		assertThat(created.getEmail()).isEqualTo("pedro@example.com");
		assertThat(created.getPasswordHash()).isNotEqualTo("senha-super-secreta");
		assertThat(encoder.matches("senha-super-secreta", created.getPasswordHash())).isTrue();
	}

	@Test
	@DisplayName("cadastro atribui o papel USER, sem prefixo ROLE_")
	void registerAssignsDefaultRoleWithoutPrefix() {
		givenDefaultRoleExists();

		User created = service.register(newUser("novo@example.com"));

		assertThat(created.getRoles())
				.extracting(Role::getName)
				.containsExactly("USER");
	}

	@Test
	@DisplayName("e-mail já cadastrado é rejeitado antes de tocar no banco")
	void registerRejectsDuplicateEmail() {
		when(users.existsByEmail("repetido@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(newUser("Repetido@Example.com")))
				.isInstanceOf(EmailAlreadyUsedException.class);

		verify(users, never()).save(any(User.class));
	}

	@Test
	@DisplayName("ausência do papel padrão falha alto, em vez de criar usuário sem permissão")
	void registerFailsWhenDefaultRoleIsMissing() {
		when(users.existsByEmail(anyString())).thenReturn(false);
		when(roles.findByName(Role.USER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register(newUser("sem-papel@example.com")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("carga inicial");
	}

	@Test
	@DisplayName("busca por e-mail também normaliza antes de consultar")
	void getByEmailNormalizes() {
		User stored = new User();
		stored.setEmail("achado@example.com");
		when(users.findByEmail("achado@example.com")).thenReturn(Optional.of(stored));

		assertThat(service.getByEmail("  Achado@EXAMPLE.com ")).isSameAs(stored);
	}

	@Test
	@DisplayName("usuário inexistente lança UserNotFoundException")
	void getByEmailThrowsWhenMissing() {
		when(users.findByEmail(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getByEmail("fantasma@example.com"))
				.isInstanceOf(UserNotFoundException.class);
	}

	// ----------------------------------------------------------------- apoio --

	private void givenDefaultRoleExists() {
		Role role = new Role();
		role.setName(Role.USER);
		when(users.existsByEmail(anyString())).thenReturn(false);
		when(roles.findByName(Role.USER)).thenReturn(Optional.of(role));
	}

	private UserService.NewUser newUser(String email) {
		return new UserService.NewUser("Pessoa", email, "senha-super-secreta", null);
	}
}
