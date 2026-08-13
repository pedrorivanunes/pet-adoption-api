package com.petadoption.api.web.dto;

import com.petadoption.api.domain.StayKind;
import com.petadoption.api.service.PetHistoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StayRequest(

		@NotNull(message = "tipo da permanência é obrigatório")
		StayKind kind,

		@NotBlank(message = "localização é obrigatória")
		@Size(max = 255, message = "localização deve ter no máximo 255 caracteres")
		String location,

		/** Pode ser anterior ao cadastro do pet — resgate quase sempre é. */
		@NotNull(message = "data de início é obrigatória")
		@PastOrPresent(message = "a permanência não começa no futuro")
		LocalDate startedOn,

		@Size(max = 1000, message = "observações devem ter no máximo 1000 caracteres")
		String notes,

		/** Organização que assume a guarda; exige vínculo de quem registra. */
		Long custodianOrganizationId) {

	public PetHistoryService.StayData toData() {
		return new PetHistoryService.StayData(kind, location, startedOn, notes, custodianOrganizationId);
	}
}
