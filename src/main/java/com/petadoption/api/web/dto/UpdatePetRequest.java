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
 * Igual ao cadastro, menos o dono: transferir um pet de tutor ou de abrigo é
 * uma operação de negócio própria — com histórico de rastreabilidade — e não um
 * campo que se troca numa edição comum.
 */
public record UpdatePetRequest(

		@NotBlank(message = "nome é obrigatório")
		@Size(max = 100, message = "nome deve ter no máximo 100 caracteres")
		String name,

		@NotBlank(message = "espécie é obrigatória (ex.: DOG, CAT)")
		@Size(max = 30, message = "espécie deve ter no máximo 30 caracteres")
		String species,

		@Size(max = 100, message = "raça deve ter no máximo 100 caracteres")
		String breed,

		PetSex sex,

		PetSize size,

		@PastOrPresent(message = "data de nascimento não pode estar no futuro")
		LocalDate birthDate,

		Boolean birthDateEstimated,

		PetStatus status,

		Boolean hasSpecialNeeds,
		Boolean hasContinuousTreatment,
		Boolean hasChronicDisease,
		Boolean requiresConstantCare,

		Boolean goodWithOtherAnimals,

		@Size(max = 1000, message = "observações de saúde devem ter no máximo 1000 caracteres")
		String healthNotes) {

	public PetService.PetData toData() {
		return new PetService.PetData(name, species, breed, sex, size, birthDate, birthDateEstimated, status,
				hasSpecialNeeds, hasContinuousTreatment, hasChronicDisease, requiresConstantCare,
				goodWithOtherAnimals, healthNotes);
	}
}
