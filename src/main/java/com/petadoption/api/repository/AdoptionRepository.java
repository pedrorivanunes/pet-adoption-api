package com.petadoption.api.repository;

import com.petadoption.api.domain.Adoption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdoptionRepository extends JpaRepository<Adoption, Long> {

	boolean existsByPet_Id(Long petId);
}
