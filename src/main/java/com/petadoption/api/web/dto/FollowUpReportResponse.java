package com.petadoption.api.web.dto;

import com.petadoption.api.domain.Adoption;
import com.petadoption.api.service.FollowUpService;

import java.time.LocalDate;
import java.util.List;

/**
 * The post-adoption follow-up report.
 *
 * <p>Besides the records, it carries what a count alone hides: which of the
 * elapsed months had no contact at all. Twenty visits in the first month and
 * silence for the next five add up to twenty, and are not a follow-up.
 */
public record FollowUpReportResponse(
		Long adoptionId,
		PetSummary pet,
		AdopterSummary adopter,
		Window window,
		Summary summary,
		List<FollowUpResponse> interactions,
		List<HealthRecordResponse> healthRecords) {

	public record PetSummary(Long id, String name, String species) {
	}

	public record AdopterSummary(Long id, String name, String email) {
	}

	public record Window(LocalDate adoptedOn, LocalDate endsOn, int minimumMonths, boolean complete) {
	}

	public record Summary(
			long daysSinceAdoption,
			int interactionCount,
			int healthRecordCount,
			List<String> monthsWithoutContact) {
	}

	public static FollowUpReportResponse from(FollowUpService.Report report) {
		Adoption adoption = report.adoption();

		return new FollowUpReportResponse(
				adoption.getId(),
				new PetSummary(adoption.getPet().getId(), adoption.getPet().getName(),
						adoption.getPet().getSpecies()),
				new AdopterSummary(adoption.getAdopter().getId(), adoption.getAdopter().getName(),
						adoption.getAdopter().getEmail()),
				new Window(adoption.getAdoptedOn(), report.windowEndsOn(), Adoption.FOLLOW_UP_MONTHS,
						report.minimumPeriodComplete()),
				new Summary(
						report.daysSinceAdoption(),
						report.interactions().size(),
						report.healthRecords().size(),
						report.monthsWithoutContact().stream().map(Object::toString).toList()),
				report.interactions().stream().map(FollowUpResponse::from).toList(),
				report.healthRecords().stream().map(HealthRecordResponse::from).toList());
	}
}
