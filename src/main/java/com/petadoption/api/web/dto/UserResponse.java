package com.petadoption.api.web.dto;

import com.petadoption.api.domain.Role;
import com.petadoption.api.domain.User;

import java.util.List;

/** Projeção pública do usuário. O hash da senha não tem representação aqui. */
public record UserResponse(
		Long id,
		String name,
		String email,
		String phone,
		List<String> authorities) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getName(),
				user.getEmail(),
				user.getPhone(),
				user.getRoles().stream().map(Role::getName).sorted().toList());
	}
}
