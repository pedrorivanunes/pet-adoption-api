package com.petadoption.api.repository;

import com.petadoption.api.domain.Adoption;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdoptionRepository extends JpaRepository<Adoption, Long> {

	boolean existsByPet_Id(Long petId);

	/**
	 * The animal's current adoption. A pet can in principle have more than one
	 * over its life (returned and adopted again), so the most recent one is what
	 * counts for the follow-up.
	 */
	@EntityGraph(attributePaths = { "pet", "adopter", "originUser", "originOrg" })
	Optional<Adoption> findFirstByPet_IdOrderByAdoptedOnDescIdDesc(Long petId);
}
