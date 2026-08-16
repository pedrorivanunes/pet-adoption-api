package com.petadoption.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * A global role, deliberately coarse-grained (USER, ADMIN).
 *
 * <p>The name is stored exactly as it will be compared -- without the
 * {@code ROLE_} prefix. That prefix is a Spring Security internal convention,
 * and concatenating it somewhere along the way is how you produce the classic
 * {@code ROLE_ROLE_ADMIN}, which breaks authorization without throwing anything.
 * Here the application uses {@code hasAuthority} with the literal name.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role {

	public static final String USER = "USER";
	public static final String ADMIN = "ADMIN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	// Equality by name, not by object identity: Role lives inside a Set on User,
	// and comparing by reference would let the same role enter twice when loaded
	// in different contexts.
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		return other instanceof Role role && Objects.equals(name, role.name);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name);
	}
}
