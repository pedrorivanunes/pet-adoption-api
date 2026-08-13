package com.petadoption.api.web.dto;

import com.petadoption.api.domain.FollowUpKind;
import com.petadoption.api.service.FollowUpService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record FollowUpRequest(

		@NotNull(message = "tipo é obrigatório (VISIT, CALL, MESSAGE ou OTHER)")
		FollowUpKind kind,

		@NotNull(message = "data é obrigatória")
		@PastOrPresent(message = "o contato não pode estar no futuro")
		LocalDate occurredOn,

		@Size(max = 1000, message = "observações devem ter no máximo 1000 caracteres")
		String notes) {

	public FollowUpService.FollowUpData toData() {
		return new FollowUpService.FollowUpData(kind, occurredOn, notes);
	}
}
