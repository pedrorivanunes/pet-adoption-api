package com.petadoption.api.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(

		@NotNull(message = "petId is required")
		Long petId,

		@Size(max = 1000, message = "message must be at most 1000 characters")
		String message) {
}
