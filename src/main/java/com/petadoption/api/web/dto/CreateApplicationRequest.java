package com.petadoption.api.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateApplicationRequest(

		@NotNull(message = "petId é obrigatório")
		Long petId,

		@Size(max = 1000, message = "mensagem deve ter no máximo 1000 caracteres")
		String message) {
}
