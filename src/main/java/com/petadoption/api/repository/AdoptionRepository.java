package com.petadoption.api.repository;

import com.petadoption.api.domain.Adoption;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdoptionRepository extends JpaRepository<Adoption, Long> {

	boolean existsByPet_Id(Long petId);

	/**
	 * A adoção vigente do animal. Um pet pode, em tese, ter mais de uma ao longo
	 * da vida (devolução e nova adoção), então a mais recente é a que vale para
	 * o acompanhamento.
	 */
	@EntityGraph(attributePaths = { "pet", "adopter", "originUser", "originOrg" })
	Optional<Adoption> findFirstByPet_IdOrderByAdoptedOnDescIdDesc(Long petId);
}
