package com.petadoption.api.repository;

import com.petadoption.api.domain.AdoptionFollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdoptionFollowUpRepository extends JpaRepository<AdoptionFollowUp, Long> {

	List<AdoptionFollowUp> findByAdoption_IdOrderByOccurredOnAscIdAsc(Long adoptionId);
}
