package com.petadoption.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Perfil de quem quer adotar: como vive e o que procura.
 *
 * <p>Compartilha a chave primária com o usuário ({@code @MapsId}), o que torna
 * a cardinalidade um-para-um um fato do schema, e não uma regra que a aplicação
 * precisa lembrar de conferir.
 */
@Entity
@Table(name = "adopter_profiles")
@Getter
@Setter
public class AdopterProfile {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;

	// ---------------------------------------------------- situação de vida --

	@Enumerated(EnumType.STRING)
	@Column(name = "housing_type", nullable = false)
	private HousingType housingType;

	@Column(name = "has_children", nullable = false)
	private boolean hasChildren;

	@Column(name = "residents_count")
	private Integer residentsCount;

	@Column(name = "has_other_pets", nullable = false)
	private boolean hasOtherPets;

	/** Fator impeditivo quando o animal exige cuidados constantes. */
	@Column(name = "has_time_availability", nullable = false)
	private boolean hasTimeAvailability = true;

	// -------------------------------------------------------- preferências --

	@Column(name = "preferred_species")
	private String preferredSpecies;

	@Column(name = "preferred_breed")
	private String preferredBreed;

	@Enumerated(EnumType.STRING)
	@Column(name = "preferred_size")
	private PetSize preferredSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "preferred_sex")
	private PetSex preferredSex;

	@Column(name = "accepts_special_needs", nullable = false)
	private boolean acceptsSpecialNeeds;

	@Column(name = "accepts_continuous_treatment", nullable = false)
	private boolean acceptsContinuousTreatment;

	@Column(name = "accepts_chronic_disease", nullable = false)
	private boolean acceptsChronicDisease;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
