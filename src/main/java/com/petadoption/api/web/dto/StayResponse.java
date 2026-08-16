package com.petadoption.api.web.dto;

import com.petadoption.api.domain.PetStay;
import com.petadoption.api.domain.StayKind;

import java.time.LocalDate;

/**
 * One stretch of the animal's public history.
 *
 * <p>When custody belonged to a private individual, the name is not exposed --
 * only the fact that there was a guardian. The history exists to tell the
 * animal's journey, and that story can be told without identifying the people in
 * it. An organization is different: it is a public entity, and knowing the
 * animal spent time at a known shelter is part of what makes an adopter
 * confident.
 */
public record StayResponse(
		Long id,
		StayKind kind,
		String location,
		Custodian custodian,
		LocalDate startedOn,
		LocalDate endedOn,
		boolean current,
		long durationInDays,
		String notes) {

	public record Custodian(String type, Long id, String name) {
	}

	public static StayResponse from(PetStay stay) {
		return new StayResponse(
				stay.getId(),
				stay.getKind(),
				stay.getLocation(),
				custodianOf(stay),
				stay.getStartedOn(),
				stay.getEndedOn(),
				stay.isOpen(),
				stay.durationInDays(),
				stay.getNotes());
	}

	private static Custodian custodianOf(PetStay stay) {
		if (stay.getCustodianOrg() != null) {
			return new Custodian("ORGANIZATION", stay.getCustodianOrg().getId(),
					stay.getCustodianOrg().getName());
		}
		if (stay.getCustodianUser() != null) {
			return new Custodian("USER", null, null);
		}
		// A period with nobody responsible -- the street, before the rescue.
		return null;
	}
}
