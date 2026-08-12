package com.petadoption.api.domain;

/**
 * Papel de uma pessoa dentro de uma organização.
 *
 * <p>É diferente de {@link Role}: aquele é global e grosso, este vale apenas
 * para os recursos de uma organização específica. Quem administra um abrigo não
 * ganha poder algum sobre outro.
 */
public enum OrgMemberRole {

	/** Administra a organização: dados, membros e pets. */
	ADMIN,

	/** Opera o dia a dia: cuida dos pets, mas não mexe na organização nem nos membros. */
	STAFF
}
