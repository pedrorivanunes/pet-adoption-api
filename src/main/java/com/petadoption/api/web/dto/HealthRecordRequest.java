package com.petadoption.api.web.dto;

import com.petadoption.api.domain.HealthRecordKind;
import com.petadoption.api.service.PetHistoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HealthRecordRequest(

		@NotNull(message = "kind is required (e.g. VACCINATION, NEUTERING)")
		HealthRecordKind kind,

		@NotNull(message = "date is required")
		@PastOrPresent(message = "a health event does not happen in the future")
		LocalDate occurredOn,

		@NotBlank(message = "description is required")
		@Size(max = 1000, message = "description must be at most 1000 characters")
		String description) {

	public PetHistoryService.HealthData toData() {
		return new PetHistoryService.HealthData(kind, occurredOn, description);
	}
}
