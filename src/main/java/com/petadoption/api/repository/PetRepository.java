package com.petadoption.api.repository;

import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * The owner associations are LAZY on the entity, but the API response needs
 * them. The entity graph fetches both in the same query: without it, either a
 * LazyInitializationException blows up outside the transaction (open-in-view is
 * off), or every pet in a listing fires an extra query -- the classic N+1.
 */
public interface PetRepository extends JpaRepository<Pet, Long> {

	@Override
	@EntityGraph(attributePaths = { "ownerUser", "ownerOrg" })
	Optional<Pet> findById(Long id);

	@EntityGraph(attributePaths = { "ownerUser", "ownerOrg" })
	Page<Pet> findByStatus(PetStatus status, Pageable pageable);

	@EntityGraph(attributePaths = { "ownerUser", "ownerOrg" })
	Page<Pet> findByStatusAndSpecies(PetStatus status, String species, Pageable pageable);

	@EntityGraph(attributePaths = { "ownerUser", "ownerOrg" })
	Page<Pet> findByOwnerUser_Id(Long ownerUserId, Pageable pageable);

	@EntityGraph(attributePaths = { "ownerUser", "ownerOrg" })
	Page<Pet> findByOwnerOrg_Id(Long ownerOrgId, Pageable pageable);
}
