package com.petadoption.api.service;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.HousingType;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.AdopterProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AdopterProfileService {

	private final AdopterProfileRepository profiles;
	private final UserService users;

	public AdopterProfileService(AdopterProfileRepository profiles, UserService users) {
		this.profiles = profiles;
		this.users = users;
	}

	public record ProfileData(
			HousingType housingType,
			Boolean hasChildren,
			Integer residentsCount,
			Boolean hasOtherPets,
			Boolean hasTimeAvailability,
			String preferredSpecies,
			String preferredBreed,
			PetSize preferredSize,
			PetSex preferredSex,
			Boolean acceptsSpecialNeeds,
			Boolean acceptsContinuousTreatment,
			Boolean acceptsChronicDisease) {
	}

	/**
	 * Cria ou atualiza o perfil de quem está autenticado.
	 *
	 * <p>É um PUT idempotente e não um POST: a pessoa tem no máximo um perfil —
	 * fato garantido pela chave primária compartilhada — então "criar" e
	 * "editar" são a mesma operação, e o cliente não precisa saber em qual dos
	 * dois casos está.
	 */
	@Transactional
	public AdopterProfile save(ProfileData data, String actorEmail) {
		User actor = users.getByEmail(actorEmail);

		AdopterProfile profile = profiles.findById(actor.getId()).orElseGet(() -> {
			AdopterProfile fresh = new AdopterProfile();
			fresh.setUser(actor);
			return fresh;
		});

		profile.setHousingType(data.housingType());
		profile.setHasChildren(isTrue(data.hasChildren()));
		profile.setResidentsCount(data.residentsCount());
		profile.setHasOtherPets(isTrue(data.hasOtherPets()));
		// Disponibilidade de tempo assume "sim" quando não informada: é o caso
		// comum, e o fator impeditivo só deve disparar com uma negativa
		// explícita da pessoa.
		profile.setHasTimeAvailability(data.hasTimeAvailability() == null || data.hasTimeAvailability());

		profile.setPreferredSpecies(normalizeSpecies(data.preferredSpecies()));
		profile.setPreferredBreed(data.preferredBreed());
		profile.setPreferredSize(data.preferredSize());
		profile.setPreferredSex(data.preferredSex());
		profile.setAcceptsSpecialNeeds(isTrue(data.acceptsSpecialNeeds()));
		profile.setAcceptsContinuousTreatment(isTrue(data.acceptsContinuousTreatment()));
		profile.setAcceptsChronicDisease(isTrue(data.acceptsChronicDisease()));

		return profiles.save(profile);
	}

	@Transactional(readOnly = true)
	public AdopterProfile getOf(String actorEmail) {
		User actor = users.getByEmail(actorEmail);
		return profiles.findById(actor.getId())
				.orElseThrow(() -> new NotFoundException(
						"Você ainda não preencheu seu perfil de adotante."));
	}

	@Transactional(readOnly = true)
	public Optional<AdopterProfile> findOf(User user) {
		return profiles.findById(user.getId());
	}

	private static boolean isTrue(Boolean value) {
		return value != null && value;
	}

	/** Mesma forma canônica usada no cadastro do pet, senão o filtro não casa. */
	private static String normalizeSpecies(String species) {
		return species == null || species.isBlank() ? null : species.trim().toUpperCase(Locale.ROOT);
	}
}
