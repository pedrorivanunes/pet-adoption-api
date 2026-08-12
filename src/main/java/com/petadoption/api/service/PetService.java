package com.petadoption.api.service;

import com.petadoption.api.domain.Organization;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.OrganizationRepository;
import com.petadoption.api.repository.PetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

@Service
public class PetService {

	private final PetRepository pets;
	private final OrganizationRepository organizations;
	private final UserService users;
	private final OrganizationAccess access;

	public PetService(PetRepository pets, OrganizationRepository organizations, UserService users,
			OrganizationAccess access) {
		this.pets = pets;
		this.organizations = organizations;
		this.users = users;
		this.access = access;
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

			// Wrappers, não primitivos: no pedido HTTP estes campos podem
			// simplesmente não vir, e "não informado" é um estado real na
			// borda. A tradução para o default do domínio acontece em apply().
			Boolean hasSpecialNeeds,
			Boolean hasContinuousTreatment,
			Boolean hasChronicDisease,
			Boolean requiresConstantCare,

			Boolean goodWithOtherAnimals,
			String healthNotes) {
	}

	/**
	 * Cadastra um pet.
	 *
	 * <p>Com {@code ownerOrganizationId}, o pet fica sob a organização — e quem
	 * cadastra precisa ter vínculo com ela. Sem esse campo, o dono é a própria
	 * pessoa autenticada. Não há caminho para cadastrar pet em nome de outra
	 * pessoa: o dono ou é você, ou é uma organização da qual você participa.
	 */
	@Transactional
	public Pet create(PetData data, Long ownerOrganizationId, String actorEmail) {
		User actor = users.getByEmail(actorEmail);

		Pet pet = new Pet();
		apply(pet, data);

		if (ownerOrganizationId != null) {
			Organization organization = organizations.findById(ownerOrganizationId)
					.orElseThrow(() -> NotFoundException.of("Organização", ownerOrganizationId));
			access.requireMember(ownerOrganizationId, actor);
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

	/** Catálogo público. Sem filtro de status, mostra quem está para adoção. */
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
		requireCanManage(pet, users.getByEmail(actorEmail));

		PetStatus requested = data.status() != null ? data.status() : pet.getStatus();
		if (pet.getStatus().isTerminal() && requested != pet.getStatus()) {
			throw new ConflictException(
					"Pet marcado como " + pet.getStatus() + " não volta a outro estado.");
		}

		apply(pet, data);
		return pets.save(pet);
	}

	@Transactional
	public void delete(Long id, String actorEmail) {
		Pet pet = getById(id);
		requireCanManage(pet, users.getByEmail(actorEmail));
		pets.delete(pet);
	}

	// ======================================================== apoio ==========

	/**
	 * Pet de pessoa só o dono administra; pet de organização, qualquer membro
	 * dela (ADMIN ou STAFF), porque cuidar dos animais é justamente o trabalho
	 * do staff.
	 */
	private void requireCanManage(Pet pet, User actor) {
		if (pet.isOwnedByOrganization()) {
			access.requireMember(pet.getOwnerOrg().getId(), actor);
			return;
		}

		if (!pet.getOwnerUser().getId().equals(actor.getId())) {
			throw new AccessDeniedException("Este pet pertence a outra pessoa");
		}
	}

	private void apply(Pet pet, PetData data) {
		pet.setName(data.name().trim());
		pet.setSpecies(normalizeSpecies(data.species()));
		pet.setBreed(data.breed());
		pet.setSex(data.sex());
		pet.setSize(data.size());
		pet.setBirthDate(data.birthDate());
		// Data de nascimento não informada como exata é tratada como estimativa:
		// para animal resgatado, esse é o caso comum.
		pet.setBirthDateEstimated(data.birthDateEstimated() == null || data.birthDateEstimated());

		// Flag ausente significa "não". Assumir o contrário marcaria animais com
		// necessidades que eles não têm.
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
	 * Espécie é texto livre — o mundo tem mais que cães e gatos — mas precisa de
	 * forma canônica, senão "gato", "Gato" e "GATO" viram três espécies
	 * distintas e o filtro do catálogo passa a mentir.
	 */
	private String normalizeSpecies(String species) {
		return species == null ? null : species.trim().toUpperCase(Locale.ROOT);
	}

	private static boolean isTrue(Boolean value) {
		return value != null && value;
	}
}
