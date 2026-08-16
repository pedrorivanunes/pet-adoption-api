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

/** A visit or interaction logged during the post-adoption follow-up. */
@Entity
@Table(name = "adoption_followups")
@Getter
@Setter
public class AdoptionFollowUp {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "adoption_id", nullable = false)
	private Adoption adoption;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FollowUpKind kind;

	@Column(name = "occurred_on", nullable = false)
	private LocalDate occurredOn;

	private String notes;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recorded_by_user_id")
	private User recordedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;
}
