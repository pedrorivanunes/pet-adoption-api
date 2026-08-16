package com.petadoption.api.domain;

/**
 * A person's role inside one organization.
 *
 * <p>This is not {@link Role}: that one is global and coarse, this one applies
 * only to the resources of one specific organization. Administering one shelter
 * grants no power whatsoever over another.
 */
public enum OrgMemberRole {

	/** Administers the organization: its data, its members and its pets. */
	ADMIN,

	/** Runs day-to-day work: manages pets, but not the organization or its members. */
	STAFF
}
