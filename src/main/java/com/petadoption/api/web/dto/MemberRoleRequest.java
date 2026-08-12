package com.petadoption.api.web.dto;

import com.petadoption.api.domain.OrgMemberRole;
import jakarta.validation.constraints.NotNull;

public record MemberRoleRequest(

		@NotNull(message = "papel é obrigatório (ADMIN ou STAFF)")
		OrgMemberRole role) {
}
