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

import java.time.OffsetDateTime;

/** One person's application to adopt a pet. */
@Entity
@Table(name = "adoption_applications")
@Getter
@Setter
public class AdoptionApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "adopter_user_id", nullable = false)
	private User adopter;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ApplicationStatus status = ApplicationStatus.PENDING;

	/** The applicant's own words: why they want to adopt, what home they offer. */
	private String message;

	/**
	 * Compatibility as computed at the moment of application. It is a snapshot,
	 * not a live value: later changes to the profile or the pet do not rewrite
	 * what the decision was based on.
	 */
	@Column(name = "compatibility_score")
	private Integer compatibilityScore;

	@Column(name = "has_blocking_factor", nullable = false)
	private boolean hasBlockingFactor;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "decided_at")
	private OffsetDateTime decidedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by_user_id")
	private User decidedBy;

	@Column(name = "decision_note")
	private String decisionNote;

	/**
	 * Records the decision in a single move: status, who decided and when. The
	 * database enforces that consistency with a CHECK, and keeping it in one
	 * place stops a new code path from updating the status and forgetting the
	 * rest.
	 */
	public void decide(ApplicationStatus outcome, User decidedBy, String note) {
		this.status = outcome;
		this.decidedBy = decidedBy;
		this.decisionNote = note;
		this.decidedAt = OffsetDateTime.now();
	}
}
