package com.petadoption.api.repository;

import com.petadoption.api.domain.OrgMemberRole;
import com.petadoption.api.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {

	Optional<OrganizationMembership> findByOrganization_IdAndUser_Id(Long organizationId, Long userId);

	// The listing exposes each member's name and email, so the user is fetched
	// in the same query instead of one query per row.
	@EntityGraph(attributePaths = { "user" })
	List<OrganizationMembership> findByOrganization_IdOrderByCreatedAtAsc(Long organizationId);

	long countByOrganization_IdAndMemberRole(Long organizationId, OrgMemberRole memberRole);
}
