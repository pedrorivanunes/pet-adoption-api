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

		@NotNull(message = "tipo de moradia é obrigatório (HOUSE, APARTMENT ou RURAL)")
		HousingType housingType,

		Boolean hasChildren,

		@PositiveOrZero(message = "número de moradores não pode ser negativo")
		@Max(value = 50, message = "número de moradores parece implausível")
		Integer residentsCount,

		Boolean hasOtherPets,

		/** Fator impeditivo para animais que exigem cuidados constantes. */
		Boolean hasTimeAvailability,

		@Size(max = 30, message = "espécie desejada deve ter no máximo 30 caracteres")
		String preferredSpecies,

		@Size(max = 100, message = "raça desejada deve ter no máximo 100 caracteres")
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
