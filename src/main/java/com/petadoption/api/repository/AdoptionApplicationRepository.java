package com.petadoption.api.repository;

import com.petadoption.api.domain.AdoptionApplication;
import com.petadoption.api.domain.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdoptionApplicationRepository extends JpaRepository<AdoptionApplication, Long> {

	@Override
	@EntityGraph(attributePaths = { "pet", "pet.ownerUser", "pet.ownerOrg", "adopter" })
	Optional<AdoptionApplication> findById(Long id);

	@EntityGraph(attributePaths = { "pet", "adopter" })
	Page<AdoptionApplication> findByAdopter_IdOrderByCreatedAtDesc(Long adopterId, Pageable pageable);

	// Sem OrderBy no nome do método: a ordenação vem do Pageable, para que o
	// relatório de compatibilidade possa ranquear por score.
	@EntityGraph(attributePaths = { "pet", "adopter" })
	Page<AdoptionApplication> findByPet_Id(Long petId, Pageable pageable);

	List<AdoptionApplication> findByPet_IdAndStatus(Long petId, ApplicationStatus status);

	boolean existsByAdopter_IdAndStatus(Long adopterId, ApplicationStatus status);

	boolean existsByPet_IdAndAdopter_IdAndStatus(Long petId, Long adopterId, ApplicationStatus status);
}
