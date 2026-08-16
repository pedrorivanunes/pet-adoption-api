package com.petadoption.api.web;

import com.petadoption.api.service.PetHistoryService;
import com.petadoption.api.web.dto.HealthRecordRequest;
import com.petadoption.api.web.dto.HealthRecordResponse;
import com.petadoption.api.web.dto.StayRequest;
import com.petadoption.api.web.dto.StayResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pets/{petId}")
public class PetHistoryController {

	private final PetHistoryService history;

	public PetHistoryController(PetHistoryService history) {
		this.history = history;
	}

	/**
	 * The traceability history -- public, like the rest of the catalogue.
	 *
	 * <p>Knowing the animal was rescued eight months ago and passed through two
	 * shelters is part of what makes someone trust an adoption. The identity of
	 * private individual guardians does not appear.
	 */
	@GetMapping("/history")
	public List<StayResponse> history(@PathVariable Long petId) {
		return history.historyOf(petId).stream().map(StayResponse::from).toList();
	}

	@PostMapping("/history")
	@ResponseStatus(HttpStatus.CREATED)
	public StayResponse addStay(@PathVariable Long petId,
			@Valid @RequestBody StayRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return StayResponse.from(history.addStay(petId, request.toData(), jwt.getSubject()));
	}

	/** The health record: clinical detail, restricted to whoever manages the animal. */
	@GetMapping("/health-records")
	public List<HealthRecordResponse> healthRecords(@PathVariable Long petId,
			@AuthenticationPrincipal Jwt jwt) {

		return history.healthRecordsOf(petId, jwt.getSubject()).stream()
				.map(HealthRecordResponse::from)
				.toList();
	}

	@PostMapping("/health-records")
	@ResponseStatus(HttpStatus.CREATED)
	public HealthRecordResponse addHealthRecord(@PathVariable Long petId,
			@Valid @RequestBody HealthRecordRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return HealthRecordResponse.from(history.addHealthRecord(petId, request.toData(), jwt.getSubject()));
	}
}
