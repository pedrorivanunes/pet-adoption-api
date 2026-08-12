package com.petadoption.api.web.dto;

import jakarta.validation.constraints.Size;

/** Justificativa opcional de quem aprova ou recusa uma candidatura. */
public record DecisionRequest(

		@Size(max = 1000, message = "justificativa deve ter no máximo 1000 caracteres")
		String note) {
}
