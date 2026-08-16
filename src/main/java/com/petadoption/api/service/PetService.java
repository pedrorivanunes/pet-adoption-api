package com.petadoption.api.service;

import com.petadoption.api.domain.Organization;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.AdoptionRepository;
import com.petadoption.api.repository.OrganizationRepository;
import com.petadoption.api.repository.PetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

@Service
public class PetService {

	private final PetRepository pets;
	private final OrganizationRepository organizations;
	private final AdoptionRepository adoptions;
	private final UserService users;
	private final OrganizationAccess organizationAccess;
	private final PetAccess petAccess;

	public PetService(PetRepository pets, OrganizationRepository organizations, AdoptionRepository adoptions,
			UserService users, OrganizationAccess organizationAccess, PetAccess petAccess) {
		this.pets = pets;
		this.organizations = organizations;
		this.adoptions = adoptions;
		this.users = users;
		this.organizationAccess = organizationAccess;
		this.petAccess = petAccess;
	}

	public record PetData(
			String name,
			String species,
			String breed,
			PetSex sex,
			PetSize size,
			LocalDate birthDate,
			Boolean birthDateEstimated,
			PetStatus status,

			// Boxed types, not primitives: in an HTTP request these fields may
			// simply be absent, and "not stated" is a real state at the edge.
			// The translation to the domain default happens in apply().
			Boolean hasSpecialNeeds,
			Boolean hasContinuousTreatment,
			Boolean hasChronicDisease,
			Boolean requiresConstantCare,

			Boolean goodWithOtherAnimals,
			String healthNotes) {
	}

	/**
	 * Registers a pet.
	 *
	 * <p>With {@code ownerOrganizationId}, the pet belongs to the organization
	 * -- and whoever registers it must be a member. Without that field, the
	 * owner is the authenticated person themselves. There is no path to register
	 * a pet on someone else's behalf: the owner is either you, or an
	 * organization you belong to.
	 */
	@Transactional
	public Pet create(PetData data, Long ownerOrganizationId, String actorEmail) {
		User actor = users.getByEmail(actorEmail);

		Pet pet = new Pet();
		apply(pet, data);

		if (ownerOrganizationId != null) {
			Organization organization = organizations.findById(ownerOrganizationId)
					.orElseThrow(() -> NotFoundException.of("Organization", ownerOrganizationId));
			organizationAccess.requireMember(ownerOrganizationId, actor);
			pet.setOwnerOrg(organization);
		}
		else {
			pet.setOwnerUser(actor);
		}

		return pets.save(pet);
	}

	@Transactional(readOnly = true)
	public Pet getById(Long id) {
		return pets.findById(id).orElseThrow(() -> NotFoundException.of("Pet", id));
	}

	/** The public catalogue. With no status filter, it shows what is up for adoption. */
	@Transactional(readOnly = true)
	public Page<Pet> list(PetStatus status, String species, Pageable pageable) {
		PetStatus effective = status != null ? status : PetStatus.AVAILABLE;

		return species == null || species.isBlank()
				? pets.findByStatus(effective, pageable)
				: pets.findByStatusAndSpecies(effective, normalizeSpecies(species), pageable);
	}

	@Transactional(readOnly = true)
	public Page<Pet> listOwnedBy(String actorEmail, Pageable pageable) {
		return pets.findByOwnerUser_Id(users.getByEmail(actorEmail).getId(), pageable);
	}

	@Transactional(readOnly = true)
	public Page<Pet> listOfOrganization(Long organizationId, Pageable pageable) {
		return pets.findByOwnerOrg_Id(organizationId, pageable);
	}

	@Transactional
	public Pet update(Long id, PetData data, String actorEmail) {
		Pet pet = getById(id);
		petAccess.requireCanManage(pet, users.getByEmail(actorEmail));

		PetStatus requested = data.status() != null ? data.status() : pet.getStatus();
		if (pet.getStatus().isTerminal() && requested != pet.getStatus()) {
			throw new ConflictException(
					"A pet marked as " + pet.getStatus() + " does not return to another state.");
		}

		apply(pet, data);
		return pets.save(pet);
	}

	@Transactional
	public void delete(Long id, String actorEmail) {
		Pet pet = getById(id);
		petAccess.requireCanManage(pet, users.getByEmail(actorEmail));

		// The adoption is history and points at the pet with RESTRICT: the
		// database would refuse the delete anyway. Catching it here swaps a raw
		// constraint error for an explanation of why the operation makes no
		// sense.
		if (adoptions.existsByPet_Id(id)) {
			throw new ConflictException(
					"This pet has an adoption on record and cannot be removed -- the history depends on it.");
		}

		pets.delete(pet);
	}

	// ======================================================= helpers =========

	private void apply(Pet pet, PetData data) {
		pet.setName(data.name().trim());
		pet.setSpecies(normalizeSpecies(data.species()));
		pet.setBreed(data.breed());
		pet.setSex(data.sex());
		pet.setSize(data.size());
		pet.setBirthDate(data.birthDate());
		// A birth date not stated as exact is treated as an estimate: for a
		// rescued animal, that is the common case.
		pet.setBirthDateEstimated(data.birthDateEstimated() == null || data.birthDateEstimated());

		// An absent flag means "no". Assuming the opposite would mark animals
		// with needs they do not have.
		pet.setHasSpecialNeeds(isTrue(data.hasSpecialNeeds()));
		pet.setHasContinuousTreatment(isTrue(data.hasContinuousTreatment()));
		pet.setHasChronicDisease(isTrue(data.hasChronicDisease()));
		pet.setRequiresConstantCare(isTrue(data.requiresConstantCare()));
		pet.setGoodWithOtherAnimals(data.goodWithOtherAnimals());
		pet.setHealthNotes(data.healthNotes());

		if (data.status() != null) {
			pet.setStatus(data.status());
		}
	}

	/**
	 * Species is free text -- the world holds more than cats and dogs -- but it
	 * needs a canonical form, otherwise "cat", "Cat" and "CAT" become three
	 * distinct species and the catalogue filter starts lying.
	 */
	private String normalizeSpecies(String species) {
		return species == null ? null : species.trim().toUpperCase(Locale.ROOT);
	}

	private static boolean isTrue(Boolean value) {
		return value != null && value;
	}
}
