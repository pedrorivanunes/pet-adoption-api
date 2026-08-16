package com.petadoption.api.service;

import com.petadoption.api.domain.OrgMemberRole;
import com.petadoption.api.domain.OrganizationMembership;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.OrganizationMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Answers one question only: what this person may do in this organization.
 *
 * <p>It sits on its own on purpose. Authorization over organization resources is
 * consulted by both the organization service and the pet service; duplicating it
 * across the two is how the rules drift apart -- and a drift here throws no
 * error, it just grants access that should have been denied.
 *
 * <p>Note that the user's global role ({@code USER}, {@code ADMIN}) plays no
 * part: administering one shelter grants no power over another.
 */
@Component
public class OrganizationAccess {

	private final OrganizationMembershipRepository memberships;

	public OrganizationAccess(OrganizationMembershipRepository memberships) {
		this.memberships = memberships;
	}

	@Transactional(readOnly = true)
	public Optional<OrgMemberRole> roleOf(Long organizationId, User user) {
		return memberships.findByOrganization_IdAndUser_Id(organizationId, user.getId())
				.map(OrganizationMembership::getMemberRole);
	}

	public boolean isMember(Long organizationId, User user) {
		return roleOf(organizationId, user).isPresent();
	}

	public boolean isAdmin(Long organizationId, User user) {
		return roleOf(organizationId, user).filter(OrgMemberRole.ADMIN::equals).isPresent();
	}

	/** Any membership will do -- ADMIN or STAFF both care for the org's pets. */
	public void requireMember(Long organizationId, User user) {
		if (!isMember(organizationId, user)) {
			throw new AccessDeniedException("You are not a member of this organization");
		}
	}

	/** Only an ADMIN touches the organization itself and its membership list. */
	public void requireAdmin(Long organizationId, User user) {
		if (!isAdmin(organizationId, user)) {
			throw new AccessDeniedException("Only administrators of this organization can do that");
		}
	}
}
