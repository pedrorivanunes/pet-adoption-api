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
 * Papel global, propositalmente de granularidade grossa (USER, ADMIN).
 *
 * <p>O nome é guardado exatamente como será comparado — sem o prefixo
 * {@code ROLE_}. Esse prefixo é convenção interna do Spring Security, e
 * concatená-lo em algum ponto do caminho é como se produz o clássico
 * {@code ROLE_ROLE_ADMIN}, que quebra a autorização sem lançar erro nenhum.
 * Aqui a aplicação usa {@code hasAuthority} com o nome literal.
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

	// Igualdade por nome, não por identidade de objeto: Role vive dentro de um
	// Set em User, e comparar por referência faria o mesmo papel entrar duas
	// vezes quando carregado em contextos diferentes.
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
