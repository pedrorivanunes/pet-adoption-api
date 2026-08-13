package com.petadoption.api.web.dto;

import com.petadoption.api.domain.HealthRecordKind;
import com.petadoption.api.domain.PetHealthRecord;

import java.time.LocalDate;

public record HealthRecordResponse(
		Long id,
		HealthRecordKind kind,
		LocalDate occurredOn,
		String description) {

	public static HealthRecordResponse from(PetHealthRecord record) {
		return new HealthRecordResponse(
				record.getId(), record.getKind(), record.getOccurredOn(), record.getDescription());
	}
}
