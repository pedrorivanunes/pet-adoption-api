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

/** Candidatura de uma pessoa a adotar um pet. */
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

	/** Texto do candidato: por que quer adotar, como é a casa, etc. */
	private String message;

	/**
	 * Compatibilidade calculada no instante da candidatura. É um retrato, não
	 * um valor vivo: mudanças posteriores no perfil ou no pet não reescrevem o
	 * que embasou a decisão.
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
	 * Registra a decisão como um movimento só: status, quem decidiu e quando.
	 * O banco exige essa consistência por CHECK, e concentrar aqui evita que
	 * algum caminho novo atualize o status e esqueça o resto.
	 */
	public void decide(ApplicationStatus outcome, User decidedBy, String note) {
		this.status = outcome;
		this.decidedBy = decidedBy;
		this.decisionNote = note;
		this.decidedAt = OffsetDateTime.now();
	}
}
