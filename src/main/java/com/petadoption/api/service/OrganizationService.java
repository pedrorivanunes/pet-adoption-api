package com.petadoption.api.service;

import com.petadoption.api.domain.OrgMemberRole;
import com.petadoption.api.domain.Organization;
import com.petadoption.api.domain.OrganizationMembership;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.OrganizationMembershipRepository;
import com.petadoption.api.repository.OrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrganizationService {

	private final OrganizationRepository organizations;
	private final OrganizationMembershipRepository memberships;
	private final UserService users;
	private final OrganizationAccess access;

	public OrganizationService(OrganizationRepository organizations,
			OrganizationMembershipRepository memberships,
			UserService users,
			OrganizationAccess access) {
		this.organizations = organizations;
		this.memberships = memberships;
		this.users = users;
		this.access = access;
	}

	public record OrganizationData(String name, String description, String email, String phone, String address) {
	}

	/**
	 * Creates the organization and links its creator as ADMIN, in one
	 * transaction.
	 *
	 * <p>If the membership failed after the organization already existed, it
	 * would be orphaned: with no administrator, nobody could edit or delete it.
	 */
	@Transactional
	public Organization create(OrganizationData data, String actorEmail) {
		User actor = users.getByEmail(actorEmail);

		Organization organization = new Organization();
		apply(organization, data);
		Organization saved = organizations.save(organization);

		OrganizationMembership membership = new OrganizationMembership();
		membership.setOrganization(saved);
		membership.setUser(actor);
		membership.setMemberRole(OrgMemberRole.ADMIN);
		memberships.save(membership);

		return saved;
	}

	@Transactional(readOnly = true)
	public Organization getById(Long id) {
		return organizations.findById(id).orElseThrow(() -> NotFoundException.of("Organization", id));
	}

	@Transactional(readOnly = true)
	public Page<Organization> list(Pageable pageable) {
		return organizations.findAll(pageable);
	}

	@Transactional
	public Organization update(Long id, OrganizationData data, String actorEmail) {
		Organization organization = getById(id);
		access.requireAdmin(id, users.getByEmail(actorEmail));

		apply(organization, data);
		return organizations.save(organization);
	}

	@Transactional
	public void delete(Long id, String actorEmail) {
		Organization organization = getById(id);
		access.requireAdmin(id, users.getByEmail(actorEmail));

		// The organization's pets point at it with ON DELETE RESTRICT, so the
		// database refuses to delete an organization that still has animals.
		// Catching it here returns a useful message instead of a raw constraint
		// error.
		organizations.delete(organization);
	}

	// ======================================================= members =========

	@Transactional(readOnly = true)
	public List<OrganizationMembership> listMembers(Long organizationId, String actorEmail) {
		getById(organizationId);
		access.requireMember(organizationId, users.getByEmail(actorEmail));

		return memberships.findByOrganization_IdOrderByCreatedAtAsc(organizationId);
	}

	@Transactional
	public OrganizationMembership addMember(Long organizationId, String memberEmail, OrgMemberRole role,
			String actorEmail) {

		Organization organization = getById(organizationId);
		access.requireAdmin(organizationId, users.getByEmail(actorEmail));

		User member = users.getByEmail(memberEmail);
		memberships.findByOrganization_IdAndUser_Id(organizationId, member.getId()).ifPresent(existing -> {
			throw new ConflictException("This person is already a member of the organization. "
					+ "To change their role, update the existing membership.");
		});

		OrganizationMembership membership = new OrganizationMembership();
		membership.setOrganization(organization);
		membership.setUser(member);
		membership.setMemberRole(role);
		return memberships.save(membership);
	}

	@Transactional
	public OrganizationMembership changeMemberRole(Long organizationId, Long memberUserId, OrgMemberRole role,
			String actorEmail) {

		access.requireAdmin(organizationId, users.getByEmail(actorEmail));
		OrganizationMembership membership = findMembership(organizationId, memberUserId);

		if (membership.getMemberRole() == OrgMemberRole.ADMIN && role != OrgMemberRole.ADMIN) {
			requireAnotherAdminExists(organizationId);
		}

		membership.setMemberRole(role);
		return memberships.save(membership);
	}

	@Transactional
	public void removeMember(Long organizationId, Long memberUserId, String actorEmail) {
		access.requireAdmin(organizationId, users.getByEmail(actorEmail));
		OrganizationMembership membership = findMembership(organizationId, memberUserId);

		if (membership.getMemberRole() == OrgMemberRole.ADMIN) {
			requireAnotherAdminExists(organizationId);
		}

		memberships.delete(membership);
	}

	// ======================================================= helpers =========

	private OrganizationMembership findMembership(Long organizationId, Long memberUserId) {
		return memberships.findByOrganization_IdAndUser_Id(organizationId, memberUserId)
				.orElseThrow(() -> NotFoundException.of("Organization membership", memberUserId));
	}

	/**
	 * Stops the organization from ending up with no administrator at all.
	 * Without this rule, the last ADMIN could demote or remove themselves and
	 * lock everyone out -- the organization would go on existing with nobody
	 * able to administer it, and there is no way back through the API itself.
	 */
	private void requireAnotherAdminExists(Long organizationId) {
		if (memberships.countByOrganization_IdAndMemberRole(organizationId, OrgMemberRole.ADMIN) <= 1) {
			throw new ConflictException(
					"This organization would be left with no administrator. Promote someone else to ADMIN first.");
		}
	}

	private void apply(Organization organization, OrganizationData data) {
		organization.setName(data.name().trim());
		organization.setDescription(data.description());
		organization.setEmail(data.email());
		organization.setPhone(data.phone());
		organization.setAddress(data.address());
	}
}
