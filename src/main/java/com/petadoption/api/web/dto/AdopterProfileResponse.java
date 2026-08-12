package com.petadoption.api.web.dto;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.HousingType;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;

public record AdopterProfileResponse(
		HousingType housingType,
		boolean hasChildren,
		Integer residentsCount,
		boolean hasOtherPets,
		boolean hasTimeAvailability,
		String preferredSpecies,
		String preferredBreed,
		PetSize preferredSize,
		PetSex preferredSex,
		boolean acceptsSpecialNeeds,
		boolean acceptsContinuousTreatment,
		boolean acceptsChronicDisease) {

	public static AdopterProfileResponse from(AdopterProfile profile) {
		return new AdopterProfileResponse(
				profile.getHousingType(),
				profile.isHasChildren(),
				profile.getResidentsCount(),
				profile.isHasOtherPets(),
				profile.isHasTimeAvailability(),
				profile.getPreferredSpecies(),
				profile.getPreferredBreed(),
				profile.getPreferredSize(),
				profile.getPreferredSex(),
				profile.isAcceptsSpecialNeeds(),
				profile.isAcceptsContinuousTreatment(),
				profile.isAcceptsChronicDisease());
	}
}
