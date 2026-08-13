package com.petadoption.api.web.dto;

import com.petadoption.api.domain.AdoptionFollowUp;
import com.petadoption.api.domain.FollowUpKind;

import java.time.LocalDate;

public record FollowUpResponse(Long id, FollowUpKind kind, LocalDate occurredOn, String notes) {

	public static FollowUpResponse from(AdoptionFollowUp followUp) {
		return new FollowUpResponse(
				followUp.getId(), followUp.getKind(), followUp.getOccurredOn(), followUp.getNotes());
	}
}
