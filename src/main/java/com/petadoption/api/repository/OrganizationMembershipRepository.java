package com.petadoption.api.repository;

import com.petadoption.api.domain.OrgMemberRole;
import com.petadoption.api.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {

	Optional<OrganizationMembership> findByOrganization_IdAndUser_Id(Long organizationId, Long userId);

	// A listagem expõe nome e e-mail de cada membro, então o usuário vem junto
	// na mesma consulta em vez de uma por linha.
	@EntityGraph(attributePaths = { "user" })
	List<OrganizationMembership> findByOrganization_IdOrderByCreatedAtAsc(Long organizationId);

	long countByOrganization_IdAndMemberRole(Long organizationId, OrgMemberRole memberRole);
}
