package com.petadoption.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/** One stretch of the animal's timeline: where it was, with whom, and for how long. */
@Entity
@Table(name = "pet_stays")
@Getter
@Setter
public class PetStay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StayKind kind;

	@Column(nullable = false)
	private String location;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "custodian_user_id")
	private User custodianUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "custodian_org_id")
	private Organization custodianOrg;

	@Column(name = "started_on", nullable = false)
	private LocalDate startedOn;

	/** Null while the animal is still there. */
	@Column(name = "ended_on")
	private LocalDate endedOn;

	private String notes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	public boolean isOpen() {
		return endedOn == null;
	}

	/** Days of the stay; if still open, counts up to today. */
	public long durationInDays() {
		return ChronoUnit.DAYS.between(startedOn, endedOn == null ? LocalDate.now() : endedOn);
	}

	public Period duration() {
		return Period.between(startedOn, endedOn == null ? LocalDate.now() : endedOn);
	}
}
