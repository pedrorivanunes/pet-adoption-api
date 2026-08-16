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
	 * Registration data.
	 *
	 * <p>A record owned by the service rather than the web layer's DTO: this
	 * keeps the business rule from depending on the shape of the incoming JSON,
	 * and keeps the arrow between the layers pointing in one direction only.
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
				"Role " + Role.USER + " not found: the initial role seed was not applied."));
	}

	/**
	 * Email is the login identifier, so it needs a single canonical form. The
	 * database also guarantees this with an index over lower(email) -- here is
	 * where the rule is applied, there is where it is enforced.
	 */
	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
