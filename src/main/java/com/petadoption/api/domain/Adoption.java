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
 * Adoção efetivada. Registro histórico, criado quando uma candidatura é
 * aprovada — e é da data daqui que se conta o acompanhamento pós-adoção.
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

	// Quem entregou o animal. A tutoria passa para o adotante na aprovação, e
	// sem este registro se perderia quem tem o dever de acompanhar os seis
	// meses seguintes. Exatamente um dos dois é preenchido.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "origin_user_id")
	private User originUser;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "origin_org_id")
	private Organization originOrg;

	@Column(name = "adopted_on", nullable = false)
	private LocalDate adoptedOn;

	/** Fim do período mínimo de acompanhamento previsto no domínio. */
	public LocalDate followUpDeadline() {
		return adoptedOn.plusMonths(FOLLOW_UP_MONTHS);
	}

	public static final int FOLLOW_UP_MONTHS = 6;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;
}
