package com.petadoption.api.repository;

import com.petadoption.api.domain.PetHealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PetHealthRecordRepository extends JpaRepository<PetHealthRecord, Long> {

	List<PetHealthRecord> findByPet_IdOrderByOccurredOnDescIdDesc(Long petId);

	List<PetHealthRecord> findByPet_IdAndOccurredOnBetweenOrderByOccurredOnAsc(
			Long petId, LocalDate from, LocalDate to);
}
