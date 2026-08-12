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
 * Responde a uma única pergunta: o que esta pessoa pode fazer nesta organização.
 *
 * <p>Existe isolada de propósito. A autorização sobre recursos de organização é
 * consultada tanto pelo serviço de organizações quanto pelo de pets; deixá-la
 * duplicada nos dois é como as regras divergem — e uma divergência aqui não dá
 * erro, apenas libera acesso que deveria negar.
 *
 * <p>Note que o papel global do usuário ({@code USER}, {@code ADMIN}) não entra
 * na conta: administrar um abrigo não dá poder algum sobre outro.
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

	/** Qualquer vínculo serve — ADMIN ou STAFF cuidam dos pets da organização. */
	public void requireMember(Long organizationId, User user) {
		if (!isMember(organizationId, user)) {
			throw new AccessDeniedException("Você não faz parte desta organização");
		}
	}

	/** Só ADMIN mexe na organização em si e no seu quadro de membros. */
	public void requireAdmin(Long organizationId, User user) {
		if (!isAdmin(organizationId, user)) {
			throw new AccessDeniedException("Apenas administradores desta organização podem fazer isso");
		}
	}
}
