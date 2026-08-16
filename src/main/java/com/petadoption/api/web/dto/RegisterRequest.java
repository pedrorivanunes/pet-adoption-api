package com.petadoption.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

		@NotBlank(message = "name is required")
		@Size(max = 120, message = "name must be at most 120 characters")
		String name,

		@NotBlank(message = "email is required")
		@Email(message = "email is not valid")
		@Size(max = 180, message = "email must be at most 180 characters")
		String email,

		@NotBlank(message = "password is required")
		@Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
		String password,

		@Size(max = 30, message = "phone must be at most 30 characters")
		String phone) {
}
