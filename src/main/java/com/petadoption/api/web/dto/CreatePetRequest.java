package com.petadoption.api.web.dto;

import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.service.PetService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreatePetRequest(

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

		// Wrappers de propósito: omitir o campo é diferente de mandá-lo false, e
		// o Jackson 3 recusa mapear ausência em primitivo. Quem decide o padrão
		// é o serviço, não o formato do JSON.
		Boolean hasSpecialNeeds,
		Boolean hasContinuousTreatment,
		Boolean hasChronicDisease,
		Boolean requiresConstantCare,

		/** Nulo significa "não se sabe" — diferente de "não convive". */
		Boolean goodWithOtherAnimals,

		@Size(max = 1000, message = "observações de saúde devem ter no máximo 1000 caracteres")
		String healthNotes,

		/**
		 * Quando informado, o pet fica sob a organização e quem cadastra precisa
		 * ter vínculo com ela. Quando ausente, o dono é quem está autenticado.
		 */
		Long ownerOrganizationId) {

	public PetService.PetData toData() {
		return new PetService.PetData(name, species, breed, sex, size, birthDate, birthDateEstimated, status,
				hasSpecialNeeds, hasContinuousTreatment, hasChronicDisease, requiresConstantCare,
				goodWithOtherAnimals, healthNotes);
	}
}
