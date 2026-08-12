package com.petadoption.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

		@NotBlank(message = "nome é obrigatório")
		@Size(max = 120, message = "nome deve ter no máximo 120 caracteres")
		String name,

		@NotBlank(message = "e-mail é obrigatório")
		@Email(message = "e-mail inválido")
		@Size(max = 180, message = "e-mail deve ter no máximo 180 caracteres")
		String email,

		@NotBlank(message = "senha é obrigatória")
		@Size(min = 8, max = 100, message = "senha deve ter entre 8 e 100 caracteres")
		String password,

		@Size(max = 30, message = "telefone deve ter no máximo 30 caracteres")
		String phone) {
}
