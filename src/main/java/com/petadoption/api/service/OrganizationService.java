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
	 * Cria a organização e vincula quem criou como ADMIN, na mesma transação.
	 *
	 * <p>Se o vínculo falhasse depois de a organização já existir, ela ficaria
	 * órfã: sem nenhum administrador, ninguém conseguiria editá-la nem apagá-la.
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
		return organizations.findById(id).orElseThrow(() -> NotFoundException.of("Organização", id));
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

		// Pets da organização apontam para ela com ON DELETE RESTRICT, então o
		// banco recusa apagar uma organização que ainda tem animais. Detectar
		// aqui devolve uma mensagem útil em vez de um erro de constraint cru.
		organizations.delete(organization);
	}

	// ======================================================= membros =========

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
			throw new ConflictException("Esta pessoa já faz parte da organização. "
					+ "Para trocar o papel dela, atualize o vínculo existente.");
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

	// ======================================================== apoio ==========

	private OrganizationMembership findMembership(Long organizationId, Long memberUserId) {
		return memberships.findByOrganization_IdAndUser_Id(organizationId, memberUserId)
				.orElseThrow(() -> NotFoundException.of("Vínculo com a organização", memberUserId));
	}

	/**
	 * Impede que a organização fique sem nenhum administrador. Sem esta regra,
	 * o último ADMIN pode se rebaixar ou se remover e trancar todo mundo do
	 * lado de fora — a organização passa a existir sem ninguém que possa
	 * administrá-la, e não há caminho de volta pela própria API.
	 */
	private void requireAnotherAdminExists(Long organizationId) {
		if (memberships.countByOrganization_IdAndMemberRole(organizationId, OrgMemberRole.ADMIN) <= 1) {
			throw new ConflictException(
					"Esta organização ficaria sem administrador. Promova outra pessoa a ADMIN antes.");
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
