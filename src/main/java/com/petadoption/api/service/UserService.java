package com.petadoption.api.service;

import com.petadoption.api.domain.Role;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.RoleRepository;
import com.petadoption.api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

	private final UserRepository users;
	private final RoleRepository roles;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.roles = roles;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Dados de cadastro.
	 *
	 * <p>Um record próprio do serviço, e não o DTO da camada web: assim a regra
	 * de negócio não passa a depender do formato do JSON de entrada, e a seta
	 * entre as camadas continua apontando numa direção só.
	 */
	public record NewUser(String name, String email, String rawPassword, String phone) {
	}

	@Transactional
	public User register(NewUser command) {
		String email = normalizeEmail(command.email());

		if (users.existsByEmail(email)) {
			throw new EmailAlreadyUsedException(email);
		}

		User user = new User();
		user.setName(command.name().trim());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(command.rawPassword()));
		user.setPhone(command.phone());
		user.addRole(defaultRole());

		return users.save(user);
	}

	@Transactional(readOnly = true)
	public User getByEmail(String email) {
		return users.findByEmail(normalizeEmail(email))
				.orElseThrow(() -> new UserNotFoundException(email));
	}

	private Role defaultRole() {
		return roles.findByName(Role.USER).orElseThrow(() -> new IllegalStateException(
				"Papel " + Role.USER + " não encontrado: a carga inicial de papéis não foi aplicada."));
	}

	/**
	 * E-mail é identificador de login, então precisa de forma canônica única.
	 * O banco também garante isso por índice sobre lower(email) — aqui é onde a
	 * regra é aplicada, lá é onde ela é imposta.
	 */
	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
