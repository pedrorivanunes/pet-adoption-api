package com.petadoption.api.web.dto;

import com.petadoption.api.domain.OrgMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Vincula uma pessoa já cadastrada à organização. */
public record MemberRequest(

		@NotBlank(message = "e-mail é obrigatório")
		@Email(message = "e-mail inválido")
		String email,

		@NotNull(message = "papel é obrigatório (ADMIN ou STAFF)")
		OrgMemberRole role) {
}
