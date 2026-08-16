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
	 * Creates or updates the authenticated person's profile.
	 *
	 * <p>It is an idempotent PUT rather than a POST: a person has at most one
	 * profile -- a fact guaranteed by the shared primary key -- so "create" and
	 * "edit" are the same operation, and the client does not need to know which
	 * of the two cases it is in.
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
		// Time availability defaults to "yes" when not stated: that is the
		// common case, and the blocker should only fire on an explicit "no"
		// from the person.
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
						"You have not filled in your adopter profile yet."));
	}

	@Transactional(readOnly = true)
	public Optional<AdopterProfile> findOf(User user) {
		return profiles.findById(user.getId());
	}

	private static boolean isTrue(Boolean value) {
		return value != null && value;
	}

	/** The same canonical form used when registering a pet, or the filter misses. */
	private static String normalizeSpecies(String species) {
		return species == null || species.isBlank() ? null : species.trim().toUpperCase(Locale.ROOT);
	}
}
