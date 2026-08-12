package com.petadoption.api.repository;

import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * As associações de dono são LAZY na entidade, mas a resposta da API precisa
 * delas. O entity graph traz as duas na mesma consulta: sem ele, ou estoura
 * LazyInitializationException fora da transação (open-in-view está desligado),
 * ou cada pet da listagem dispara uma consulta extra — o clássico N+1.
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
