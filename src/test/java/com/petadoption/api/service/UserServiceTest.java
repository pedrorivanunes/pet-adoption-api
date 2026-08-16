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
 * Registration rules in isolation -- no Spring, no database. What needs real
 * infrastructure is covered by the integration tests.
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
	@DisplayName("registration stores the normalised email and the hashed password")
	void registerNormalizesEmailAndHashesPassword() {
		givenDefaultRoleExists();

		User created = service.register(new UserService.NewUser(
				"  Pedro  ", "  Pedro@Example.COM ", "a-very-secret-password", "51999990000"));

		assertThat(created.getName()).isEqualTo("Pedro");
		assertThat(created.getEmail()).isEqualTo("pedro@example.com");
		assertThat(created.getPasswordHash()).isNotEqualTo("a-very-secret-password");
		assertThat(encoder.matches("a-very-secret-password", created.getPasswordHash())).isTrue();
	}

	@Test
	@DisplayName("registration assigns the USER role, without the ROLE_ prefix")
	void registerAssignsDefaultRoleWithoutPrefix() {
		givenDefaultRoleExists();

		User created = service.register(newUser("novo@example.com"));

		assertThat(created.getRoles())
				.extracting(Role::getName)
				.containsExactly("USER");
	}

	@Test
	@DisplayName("an already-registered email is rejected before touching the database")
	void registerRejectsDuplicateEmail() {
		when(users.existsByEmail("repetido@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(newUser("Repetido@Example.com")))
				.isInstanceOf(EmailAlreadyUsedException.class);

		verify(users, never()).save(any(User.class));
	}

	@Test
	@DisplayName("a missing default role fails loudly instead of creating a user with no permissions")
	void registerFailsWhenDefaultRoleIsMissing() {
		when(users.existsByEmail(anyString())).thenReturn(false);
		when(roles.findByName(Role.USER)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register(newUser("no-role@example.com")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("initial role seed");
	}

	@Test
	@DisplayName("lookup by email normalises before querying too")
	void getByEmailNormalizes() {
		User stored = new User();
		stored.setEmail("achado@example.com");
		when(users.findByEmail("achado@example.com")).thenReturn(Optional.of(stored));

		assertThat(service.getByEmail("  Achado@EXAMPLE.com ")).isSameAs(stored);
	}

	@Test
	@DisplayName("a missing user throws UserNotFoundException")
	void getByEmailThrowsWhenMissing() {
		when(users.findByEmail(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getByEmail("fantasma@example.com"))
				.isInstanceOf(UserNotFoundException.class);
	}

	// --------------------------------------------------------------- helpers --

	private void givenDefaultRoleExists() {
		Role role = new Role();
		role.setName(Role.USER);
		when(users.existsByEmail(anyString())).thenReturn(false);
		when(roles.findByName(Role.USER)).thenReturn(Optional.of(role));
	}

	private UserService.NewUser newUser(String email) {
		return new UserService.NewUser("Person", email, "a-very-secret-password", null);
	}
}
