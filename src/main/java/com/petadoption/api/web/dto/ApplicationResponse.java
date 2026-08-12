package com.petadoption.api.web.dto;

import com.petadoption.api.domain.AdoptionApplication;
import com.petadoption.api.domain.ApplicationStatus;
import com.petadoption.api.domain.PetStatus;

import java.time.OffsetDateTime;

public record ApplicationResponse(
		Long id,
		ApplicationStatus status,
		String message,
		PetSummary pet,
		AdopterSummary adopter,
		OffsetDateTime createdAt,
		OffsetDateTime decidedAt,
		String decisionNote) {

	public record PetSummary(Long id, String name, String species, PetStatus status) {
	}

	public record AdopterSummary(Long id, String name, String email) {
	}

	public static ApplicationResponse from(AdoptionApplication application) {
		return new ApplicationResponse(
				application.getId(),
				application.getStatus(),
				application.getMessage(),
				new PetSummary(
						application.getPet().getId(),
						application.getPet().getName(),
						application.getPet().getSpecies(),
						application.getPet().getStatus()),
				new AdopterSummary(
						application.getAdopter().getId(),
						application.getAdopter().getName(),
						application.getAdopter().getEmail()),
				application.getCreatedAt(),
				application.getDecidedAt(),
				application.getDecisionNote());
	}
}
