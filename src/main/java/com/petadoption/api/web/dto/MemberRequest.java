package com.petadoption.api.web.dto;

import com.petadoption.api.domain.OrgMemberRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Links an already-registered person to the organization. */
public record MemberRequest(

		@NotBlank(message = "email is required")
		@Email(message = "email is not valid")
		String email,

		@NotNull(message = "role is required (ADMIN or STAFF)")
		OrgMemberRole role) {
}
