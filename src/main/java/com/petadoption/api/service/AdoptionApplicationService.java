package com.petadoption.api.service;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.Adoption;
import com.petadoption.api.domain.AdoptionApplication;
import com.petadoption.api.domain.ApplicationStatus;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.domain.StayKind;
import com.petadoption.api.domain.User;
import com.petadoption.api.domain.compatibility.CompatibilityCalculator;
import com.petadoption.api.domain.compatibility.CompatibilityResult;
import com.petadoption.api.repository.AdoptionApplicationRepository;
import com.petadoption.api.repository.AdoptionRepository;
import com.petadoption.api.repository.PetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdoptionApplicationService {

	private final AdoptionApplicationRepository applications;
	private final AdoptionRepository adoptions;
	private final PetRepository pets;
	private final UserService users;
	private final AdopterProfileService profiles;
	private final PetAccess petAccess;
	private final CompatibilityCalculator calculator;
	private final PetHistoryService history;

	public AdoptionApplicationService(AdoptionApplicationRepository applications, AdoptionRepository adoptions,
			PetRepository pets, UserService users, AdopterProfileService profiles, PetAccess petAccess,
			CompatibilityCalculator calculator, PetHistoryService history) {
		this.applications = applications;
		this.adoptions = adoptions;
		this.pets = pets;
		this.users = users;
		this.profiles = profiles;
		this.petAccess = petAccess;
		this.calculator = calculator;
		this.history = history;
	}

	/**
	 * Records the authenticated person's application for a pet.
	 *
	 * <p>The four conditions checked here are real business rules, not form
	 * validation: each one has a reason the client needs to understand from the
	 * message.
	 */
	@Transactional
	public AdoptionApplication apply(Long petId, String message, String actorEmail) {
		User actor = users.getByEmail(actorEmail);
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));

		// 1. Without a profile there is no way to assess compatibility or to
		//    discuss the adoption -- and the profile is what feeds the ranking.
		AdopterProfile profile = profiles.findOf(actor).orElseThrow(() -> new ConflictException(
				"Preencha seu perfil de adopter antes de se candidatar."));

		// 2. Whoever manages the pet does not apply for it: they would be
		//    approving themselves.
		if (petAccess.canManage(pet, actor)) {
			throw new ConflictException("You are already responsible for this pet.");
		}

		// 3. A lost, deceased or already adopted pet takes no applications.
		if (pet.getStatus() != PetStatus.AVAILABLE) {
			throw new ConflictException("This pet is not available for adoption.");
		}

		// 4. One pet at a time. The partial unique index in the database is what
		//    actually guarantees it; this check exists so the message is clear.
		if (applications.existsByAdopter_IdAndStatus(actor.getId(), ApplicationStatus.PENDING)) {
			throw new ConflictException(
					"You already have an application in progress. Cancel it before applying for another pet.");
		}

		AdoptionApplication application = new AdoptionApplication();
		application.setPet(pet);
		application.setAdopter(actor);
		application.setMessage(message);

		// Compatibility stored as a snapshot of this moment. An application with
		// a blocker is not rejected automatically: it is recorded and flagged,
		// because there may be context the records do not capture, and the
		// decision belongs to whoever cares for the animal.
		CompatibilityResult compatibility = calculator.evaluate(pet, profile);
		application.setCompatibilityScore(compatibility.score());
		application.setHasBlockingFactor(compatibility.blocked());

		return applications.save(application);
	}

	@Transactional(readOnly = true)
	public Page<AdoptionApplication> listMine(String actorEmail, Pageable pageable) {
		User actor = users.getByEmail(actorEmail);
		return applications.findByAdopter_IdOrderByCreatedAtDesc(actor.getId(), pageable);
	}

	/** Applications received for a pet -- visible to whoever manages it. */
	@Transactional(readOnly = true)
	public Page<AdoptionApplication> listForPet(Long petId, String actorEmail, Pageable pageable) {
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));
		petAccess.requireCanManage(pet, users.getByEmail(actorEmail));

		return applications.findByPet_Id(petId, pageable);
	}

	/** Visible to the applicant and to whoever manages the pet -- nobody else. */
	@Transactional(readOnly = true)
	public AdoptionApplication getById(Long id, String actorEmail) {
		AdoptionApplication application = find(id);
		User actor = users.getByEmail(actorEmail);

		boolean isAdopter = application.getAdopter().getId().equals(actor.getId());
		if (!isAdopter && !petAccess.canManage(application.getPet(), actor)) {
			throw new AccessDeniedException("This application is neither yours nor for a pet you manage");
		}

		return application;
	}

	/**
	 * Approves the application and closes the pet's cycle in a single
	 * transaction: records the adoption, marks the pet as adopted and rejects
	 * the remaining pending applications.
	 *
	 * <p>Leaving those three things to separate calls would open a window in
	 * which the pet shows as adopted while other people are still waiting for an
	 * answer -- or worse, two approvals for the same animal.
	 */
	@Transactional
	public AdoptionApplication approve(Long id, String note, String actorEmail) {
		AdoptionApplication application = find(id);
		User actor = users.getByEmail(actorEmail);

		petAccess.requireCanManage(application.getPet(), actor);
		requirePending(application);

		application.decide(ApplicationStatus.APPROVED, actor, note);

		Pet pet = application.getPet();
		User adopter = application.getAdopter();
		LocalDate today = LocalDate.now();

		Adoption adoption = new Adoption();
		adoption.setApplication(application);
		adoption.setPet(pet);
		adoption.setAdopter(adopter);
		adoption.setAdoptedOn(today);
		// The origin is captured before the transfer: after it the pet already
		// points at the adopter, and whoever handed the animal over -- precisely
		// the party who owes the follow-up -- would have been lost.
		adoption.setOriginUser(pet.getOwnerUser());
		adoption.setOriginOrg(pet.getOwnerOrg());
		adoptions.save(adoption);

		// Adopting means becoming the guardian. Without this transfer, the
		// shelter would still show as the owner of an animal it no longer has.
		pet.setStatus(PetStatus.ADOPTED);
		pet.setOwnerUser(adopter);
		pet.setOwnerOrg(null);
		pets.save(pet);

		history.openStay(pet, StayKind.ADOPTION, "Lar de " + adopter.getName(), today,
				"Adoption approved", adopter, null);

		rejectRemainingCandidates(application, actor);

		return applications.save(application);
	}

	@Transactional
	public AdoptionApplication reject(Long id, String note, String actorEmail) {
		AdoptionApplication application = find(id);
		User actor = users.getByEmail(actorEmail);

		petAccess.requireCanManage(application.getPet(), actor);
		requirePending(application);

		application.decide(ApplicationStatus.REJECTED, actor, note);
		return applications.save(application);
	}

	/** The applicant withdrawing -- frees their slot up for another pet. */
	@Transactional
	public AdoptionApplication cancel(Long id, String actorEmail) {
		AdoptionApplication application = find(id);
		User actor = users.getByEmail(actorEmail);

		if (!application.getAdopter().getId().equals(actor.getId())) {
			throw new AccessDeniedException("Only the applicant can withdraw the application");
		}
		requirePending(application);

		application.decide(ApplicationStatus.CANCELED, actor, null);
		return applications.save(application);
	}

	// ======================================================= helpers =========

	private void rejectRemainingCandidates(AdoptionApplication approved, User decidedBy) {
		List<AdoptionApplication> stillPending = applications
				.findByPet_IdAndStatus(approved.getPet().getId(), ApplicationStatus.PENDING);

		for (AdoptionApplication other : stillPending) {
			if (other.getId().equals(approved.getId())) {
				continue;
			}
			other.decide(ApplicationStatus.REJECTED, decidedBy,
					"Another application was approved for this pet.");
		}

		applications.saveAll(stillPending);
	}

	private AdoptionApplication find(Long id) {
		return applications.findById(id).orElseThrow(() -> NotFoundException.of("Application", id));
	}

	private void requirePending(AdoptionApplication application) {
		if (application.getStatus().isFinal()) {
			throw new ConflictException(
					"This application has already been decided (" + application.getStatus() + ").");
		}
	}
}
