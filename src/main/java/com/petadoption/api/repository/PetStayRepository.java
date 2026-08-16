package com.petadoption.api.repository;

import com.petadoption.api.domain.PetStay;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PetStayRepository extends JpaRepository<PetStay, Long> {

	@EntityGraph(attributePaths = { "custodianUser", "custodianOrg" })
	List<PetStay> findByPet_IdOrderByStartedOnAscIdAsc(Long petId);

	/** The current stay -- at most one, guaranteed by a partial unique index. */
	Optional<PetStay> findByPet_IdAndEndedOnIsNull(Long petId);
}
