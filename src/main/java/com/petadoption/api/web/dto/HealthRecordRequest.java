package com.petadoption.api.web.dto;

import com.petadoption.api.domain.HealthRecordKind;
import com.petadoption.api.service.PetHistoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HealthRecordRequest(

		@NotNull(message = "tipo é obrigatório (ex.: VACCINATION, NEUTERING)")
		HealthRecordKind kind,

		@NotNull(message = "data é obrigatória")
		@PastOrPresent(message = "um evento de saúde não acontece no futuro")
		LocalDate occurredOn,

		@NotBlank(message = "descrição é obrigatória")
		@Size(max = 1000, message = "descrição deve ter no máximo 1000 caracteres")
		String description) {

	public PetHistoryService.HealthData toData() {
		return new PetHistoryService.HealthData(kind, occurredOn, description);
	}
}
