package com.petadoption.api.web.dto;

import com.petadoption.api.domain.StayKind;
import com.petadoption.api.service.PetHistoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StayRequest(

		@NotNull(message = "stay kind is required")
		StayKind kind,

		@NotBlank(message = "location is required")
		@Size(max = 255, message = "location must be at most 255 characters")
		String location,

		/** May predate the pet's registration -- a rescue almost always does. */
		@NotNull(message = "start date is required")
		@PastOrPresent(message = "a stay does not start in the future")
		LocalDate startedOn,

		@Size(max = 1000, message = "notes must be at most 1000 characters")
		String notes,

		/** The organization taking custody; requires membership from whoever records it. */
		Long custodianOrganizationId) {

	public PetHistoryService.StayData toData() {
		return new PetHistoryService.StayData(kind, location, startedOn, notes, custodianOrganizationId);
	}
}
