package com.petadoption.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A completed adoption. A historical record, created when an application is
 * approved -- and the date here is what the post-adoption follow-up counts from.
 */
@Entity
@Table(name = "adoptions")
@Getter
@Setter
public class Adoption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false, unique = true)
	private AdoptionApplication application;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "adopter_user_id", nullable = false)
	private User adopter;

	// Who handed the animal over. Guardianship passes to the adopter on
	// approval, and without this record we would lose track of who owes the
	// follow-up over the next six months. Exactly one of the two is set.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "origin_user_id")
	private User originUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "origin_org_id")
	private Organization originOrg;

	@Column(name = "adopted_on", nullable = false)
	private LocalDate adoptedOn;

	/** End of the minimum follow-up period the domain requires. */
	public LocalDate followUpDeadline() {
		return adoptedOn.plusMonths(FOLLOW_UP_MONTHS);
	}

	public static final int FOLLOW_UP_MONTHS = 6;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;
}
