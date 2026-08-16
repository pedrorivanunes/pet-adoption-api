package com.petadoption.api.web.dto;

import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.PetStatus;

import java.time.LocalDate;
import java.time.Period;

public record PetResponse(
		Long id,
		String name,
		String species,
		String breed,
		PetSex sex,
		PetSize size,
		LocalDate birthDate,
		boolean birthDateEstimated,
		Integer ageYears,
		PetStatus status,
		Owner owner,
		boolean hasSpecialNeeds,
		boolean hasContinuousTreatment,
		boolean hasChronicDisease,
		boolean requiresConstantCare,
		Boolean goodWithOtherAnimals,
		String healthNotes) {

	public record Owner(String type, Long id, String name) {
	}

	public static PetResponse from(Pet pet) {
		return new PetResponse(
				pet.getId(),
				pet.getName(),
				pet.getSpecies(),
				pet.getBreed(),
				pet.getSex(),
				pet.getSize(),
				pet.getBirthDate(),
				pet.isBirthDateEstimated(),
				ageOf(pet.getBirthDate()),
				pet.getStatus(),
				ownerOf(pet),
				pet.isHasSpecialNeeds(),
				pet.isHasContinuousTreatment(),
				pet.isHasChronicDisease(),
				pet.isRequiresConstantCare(),
				pet.getGoodWithOtherAnimals(),
				pet.getHealthNotes());
	}

	// Age is derived from the birth date, never stored: a stored number ages
	// wrong.
	private static Integer ageOf(LocalDate birthDate) {
		return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
	}

	private static Owner ownerOf(Pet pet) {
		return pet.isOwnedByOrganization()
				? new Owner("ORGANIZATION", pet.getOwnerOrg().getId(), pet.getOwnerOrg().getName())
				: new Owner("USER", pet.getOwnerUser().getId(), pet.getOwnerUser().getName());
	}
}
