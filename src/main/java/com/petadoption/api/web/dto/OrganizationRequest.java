package com.petadoption.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(

		@NotBlank(message = "nome é obrigatório")
		@Size(max = 150, message = "nome deve ter no máximo 150 caracteres")
		String name,

		@Size(max = 500, message = "descrição deve ter no máximo 500 caracteres")
		String description,

		@Email(message = "e-mail inválido")
		@Size(max = 180, message = "e-mail deve ter no máximo 180 caracteres")
		String email,

		@Size(max = 30, message = "telefone deve ter no máximo 30 caracteres")
		String phone,

		@Size(max = 255, message = "endereço deve ter no máximo 255 caracteres")
		String address) {
}
