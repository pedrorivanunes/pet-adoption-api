package com.petadoption.api.web.dto;

import com.petadoption.api.domain.FollowUpKind;
import com.petadoption.api.service.FollowUpService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record FollowUpRequest(

		@NotNull(message = "kind is required (VISIT, CALL, MESSAGE or OTHER)")
		FollowUpKind kind,

		@NotNull(message = "date is required")
		@PastOrPresent(message = "the contact cannot be in the future")
		LocalDate occurredOn,

		@Size(max = 1000, message = "notes must be at most 1000 characters")
		String notes) {

	public FollowUpService.FollowUpData toData() {
		return new FollowUpService.FollowUpData(kind, occurredOn, notes);
	}
}
