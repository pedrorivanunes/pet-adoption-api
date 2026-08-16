package com.petadoption.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(

		@NotBlank(message = "name is required")
		@Size(max = 150, message = "name must be at most 150 characters")
		String name,

		@Size(max = 500, message = "description must be at most 500 characters")
		String description,

		@Email(message = "email is not valid")
		@Size(max = 180, message = "email must be at most 180 characters")
		String email,

		@Size(max = 30, message = "phone must be at most 30 characters")
		String phone,

		@Size(max = 255, message = "address must be at most 255 characters")
		String address) {
}
