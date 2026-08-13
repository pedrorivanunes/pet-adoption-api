package com.petadoption.api.web.dto;

import com.petadoption.api.domain.PetStay;
import com.petadoption.api.domain.StayKind;

import java.time.LocalDate;

/**
 * Um trecho do histórico público do animal.
 *
 * <p>Quando a guarda era de uma pessoa física, o nome não é exposto — apenas o
 * fato de haver um tutor. O histórico existe para contar a trajetória do
 * animal, e essa história se conta sem identificar quem passou por ela.
 * Organização é diferente: é entidade pública, e saber que o animal esteve num
 * abrigo conhecido é parte do que dá confiança à adoção.
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
		// Período sem responsável — a rua, antes do resgate.
		return null;
	}
}
