package com.petadoption.api.web.dto;

import jakarta.validation.constraints.Size;

/** Optional reasoning from whoever approves or rejects an application. */
public record DecisionRequest(

		@Size(max = 1000, message = "reason must be at most 1000 characters")
		String note) {
}
