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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pets")
@Getter
@Setter
public class Pet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String species;

	private String breed;

	@Enumerated(EnumType.STRING)
	private PetSex sex;

	@Enumerated(EnumType.STRING)
	private PetSize size;

	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "birth_date_estimated", nullable = false)
	private boolean birthDateEstimated = true;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PetStatus status = PetStatus.AVAILABLE;

	// Dono é pessoa OU organização — o banco garante por CHECK que é
	// exatamente um dos dois.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_user_id")
	private User ownerUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_org_id")
	private Organization ownerOrg;

	@Column(name = "has_special_needs", nullable = false)
	private boolean hasSpecialNeeds;

	@Column(name = "has_continuous_treatment", nullable = false)
	private boolean hasContinuousTreatment;

	@Column(name = "has_chronic_disease", nullable = false)
	private boolean hasChronicDisease;

	@Column(name = "requires_constant_care", nullable = false)
	private boolean requiresConstantCare;

	// Objeto, e não primitivo, de propósito: null aqui significa "não se sabe",
	// que é diferente de "não convive". Só o segundo é fator impeditivo.
	@Column(name = "good_with_other_animals")
	private Boolean goodWithOtherAnimals;

	@Column(name = "health_notes")
	private String healthNotes;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	public boolean isOwnedByOrganization() {
		return ownerOrg != null;
	}
}
