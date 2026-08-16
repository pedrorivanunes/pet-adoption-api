package com.petadoption.api.web.dto;

import com.petadoption.api.domain.HousingType;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.service.AdopterProfileService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdopterProfileRequest(

		@NotNull(message = "housing type is required (HOUSE, APARTMENT or RURAL)")
		HousingType housingType,

		Boolean hasChildren,

		@PositiveOrZero(message = "number of residents cannot be negative")
		@Max(value = 50, message = "number of residents looks implausible")
		Integer residentsCount,

		Boolean hasOtherPets,

		/** A blocker for animals that require constant care. */
		Boolean hasTimeAvailability,

		@Size(max = 30, message = "preferred species must be at most 30 characters")
		String preferredSpecies,

		@Size(max = 100, message = "preferred breed must be at most 100 characters")
		String preferredBreed,

		PetSize preferredSize,

		PetSex preferredSex,

		Boolean acceptsSpecialNeeds,
		Boolean acceptsContinuousTreatment,
		Boolean acceptsChronicDisease) {

	public AdopterProfileService.ProfileData toData() {
		return new AdopterProfileService.ProfileData(housingType, hasChildren, residentsCount, hasOtherPets,
				hasTimeAvailability, preferredSpecies, preferredBreed, preferredSize, preferredSex,
				acceptsSpecialNeeds, acceptsContinuousTreatment, acceptsChronicDisease);
	}
}
