package com.petadoption.api.security;

import com.petadoption.api.domain.Role;
import com.petadoption.api.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	public AppUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

		com.petadoption.api.domain.User user = users.findByEmail(normalized)
				.orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));

		// O nome do papel vira a autoridade sem qualquer transformação. Esta é
		// a única tradução domínio -> Spring Security no sistema, e ela é a
		// identidade de propósito.
		List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
				.map(Role::getName)
				.map(SimpleGrantedAuthority::new)
				.toList();

		return User.withUsername(user.getEmail())
				.password(user.getPasswordHash())
				.authorities(authorities)
				.build();
	}
}
