package com.petadoption.api.web.dto;

import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.service.PetService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Same as registration, minus the owner: moving a pet to another guardian or
 * shelter is a business operation of its own -- with a traceability record --
 * and not a field you swap in an ordinary edit.
 */
public record UpdatePetRequest(

		@NotBlank(message = "name is required")
		@Size(max = 100, message = "name must be at most 100 characters")
		String name,

		@NotBlank(message = "species is required (e.g. DOG, CAT)")
		@Size(max = 30, message = "species must be at most 30 characters")
		String species,

		@Size(max = 100, message = "breed must be at most 100 characters")
		String breed,

		PetSex sex,

		PetSize size,

		@PastOrPresent(message = "birth date cannot be in the future")
		LocalDate birthDate,

		Boolean birthDateEstimated,

		PetStatus status,

		Boolean hasSpecialNeeds,
		Boolean hasContinuousTreatment,
		Boolean hasChronicDisease,
		Boolean requiresConstantCare,

		Boolean goodWithOtherAnimals,

		@Size(max = 1000, message = "health notes must be at most 1000 characters")
		String healthNotes) {

	public PetService.PetData toData() {
		return new PetService.PetData(name, species, breed, sex, size, birthDate, birthDateEstimated, status,
				hasSpecialNeeds, hasContinuousTreatment, hasChronicDisease, requiresConstantCare,
				goodWithOtherAnimals, healthNotes);
	}
}
