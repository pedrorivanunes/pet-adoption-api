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
 * Checks that the Flyway migrations apply to a clean database and produce the
 * expected schema.
 *
 * <p>The Postgres container comes up empty on every run, so this test exercises
 * the path that hurts most when it breaks: a brand-new environment. A migration
 * that only works against an existing database fails here.
 */
@IntegrationTest
@Transactional
class SchemaMigrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("migrations create every core table")
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
	@DisplayName("global roles are seeded without the ROLE_ prefix")
	void seedsGlobalRolesWithoutRolePrefix() {
		List<String> roles = jdbc.queryForList(
				"SELECT name FROM roles ORDER BY name", String.class);

		assertThat(roles).containsExactly("ADMIN", "USER");
	}

	@Test
	@DisplayName("user email is unique regardless of case")
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
	@DisplayName("a pet needs exactly one owner: a person or an organization")
	void petRequiresExactlyOneOwner() {
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO pets (name, species, status)
				VALUES ('Sem Dono', 'DOG', 'AVAILABLE')
				"""))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("a pet status outside the domain is rejected by the database")
	void petStatusIsConstrained() {
		Long orgId = jdbc.queryForObject("""
				INSERT INTO organizations (name) VALUES ('Test Shelter') RETURNING id
				""", Long.class);

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO pets (name, species, status, owner_org_id)
				VALUES ('Status Inválido', 'CAT', 'SUMIU', ?)
				""", orgId))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}
}
