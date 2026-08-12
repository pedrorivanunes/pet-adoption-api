package com.petadoption.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que as migrations do Flyway aplicam num banco limpo e produzem o
 * schema esperado.
 *
 * <p>O container do Postgres sobe zerado a cada execução, então este teste
 * exercita o caminho que mais dói quando quebra: o de um ambiente novo. Uma
 * migration que só funciona sobre um banco já existente falha aqui.
 */
@IntegrationTest
@Transactional
class SchemaMigrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("as migrations criam todas as tabelas do núcleo")
	void migrationsCreateCoreTables() {
		List<String> tables = jdbc.queryForList("""
				SELECT table_name
				  FROM information_schema.tables
				 WHERE table_schema = 'public'
				""", String.class);

		assertThat(tables).contains(
				"users",
				"roles",
				"user_roles",
				"organizations",
				"organization_memberships",
				"pets");
	}

	@Test
	@DisplayName("os papéis globais são semeados sem o prefixo ROLE_")
	void seedsGlobalRolesWithoutRolePrefix() {
		List<String> roles = jdbc.queryForList(
				"SELECT name FROM roles ORDER BY name", String.class);

		assertThat(roles).containsExactly("ADMIN", "USER");
	}

	@Test
	@DisplayName("e-mail de usuário é único ignorando maiúsculas")
	void userEmailIsUniqueCaseInsensitively() {
		jdbc.update("""
				INSERT INTO users (name, email, password_hash)
				VALUES ('Maria', 'maria@example.com', 'hash')
				""");

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO users (name, email, password_hash)
				VALUES ('Outra Maria', 'MARIA@example.com', 'hash')
				"""))
				.isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
	}

	@Test
	@DisplayName("pet precisa de exatamente um dono: pessoa ou organização")
	void petRequiresExactlyOneOwner() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO pets (name, species, status)
				VALUES ('Sem Dono', 'DOG', 'AVAILABLE')
				"""))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("status de pet fora do domínio é rejeitado pelo banco")
	void petStatusIsConstrained() {
		Long orgId = jdbc.queryForObject("""
				INSERT INTO organizations (name) VALUES ('Abrigo Teste') RETURNING id
				""", Long.class);

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO pets (name, species, status, owner_org_id)
				VALUES ('Status Inválido', 'CAT', 'SUMIU', ?)
				""", orgId))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}
}
