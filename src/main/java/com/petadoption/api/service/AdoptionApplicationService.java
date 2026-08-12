package com.petadoption.api.service;

import com.petadoption.api.domain.Adoption;
import com.petadoption.api.domain.AdoptionApplication;
import com.petadoption.api.domain.ApplicationStatus;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.domain.User;
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

	public AdoptionApplicationService(AdoptionApplicationRepository applications, AdoptionRepository adoptions,
			PetRepository pets, UserService users, AdopterProfileService profiles, PetAccess petAccess) {
		this.applications = applications;
		this.adoptions = adoptions;
		this.pets = pets;
		this.users = users;
		this.profiles = profiles;
		this.petAccess = petAccess;
	}

	/**
	 * Registra a candidatura de quem está autenticado a um pet.
	 *
	 * <p>As quatro condições verificadas aqui são regras de negócio de verdade,
	 * não validação de formulário: cada uma tem um motivo que o cliente precisa
	 * entender pela mensagem.
	 */
	@Transactional
	public AdoptionApplication apply(Long petId, String message, String actorEmail) {
		User actor = users.getByEmail(actorEmail);
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));

		// 1. Sem perfil não há como avaliar compatibilidade nem conversar sobre
		//    a adoção — e é o perfil que alimenta o ranqueamento.
		profiles.findOf(actor).orElseThrow(() -> new ConflictException(
				"Preencha seu perfil de adotante antes de se candidatar."));

		// 2. Quem cuida do pet não se candidata a ele: aprovaria a si mesmo.
		if (petAccess.canManage(pet, actor)) {
			throw new ConflictException("Você já é responsável por este pet.");
		}

		// 3. Pet perdido, falecido ou já adotado não recebe candidatura.
		if (pet.getStatus() != PetStatus.AVAILABLE) {
			throw new ConflictException("Este pet não está disponível para adoção.");
		}

		// 4. Um pet por vez. O índice único parcial no banco é quem garante de
		//    fato; esta checagem existe para a mensagem ser compreensível.
		if (applications.existsByAdopter_IdAndStatus(actor.getId(), ApplicationStatus.PENDING)) {
			throw new ConflictException(
					"Você já tem uma candidatura em andamento. Cancele-a antes de se candidatar a outro pet.");
		}

		AdoptionApplication application = new AdoptionApplication();
		application.setPet(pet);
		application.setAdopter(actor);
		application.setMessage(message);
		return applications.save(application);
	}

	@Transactional(readOnly = true)
	public Page<AdoptionApplication> listMine(String actorEmail, Pageable pageable) {
		User actor = users.getByEmail(actorEmail);
		return applications.findByAdopter_IdOrderByCreatedAtDesc(actor.getId(), pageable);
	}

	/** Candidaturas recebidas por um pet — visível a quem o administra. */
	@Transactional(readOnly = true)
	public Page<AdoptionApplication> listForPet(Long petId, String actorEmail, Pageable pageable) {
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));
		petAccess.requireCanManage(pet, users.getByEmail(actorEmail));

		return applications.findByPet_IdOrderByCreatedAtDesc(petId, pageable);
	}

	/** Visível ao candidato e a quem administra o pet — a ninguém mais. */
	@Transactional(readOnly = true)
	public AdoptionApplication getById(Long id, String actorEmail) {
		AdoptionApplication application = find(id);
		User actor = users.getByEmail(actorEmail);

		boolean isAdopter = application.getAdopter().getId().equals(actor.getId());
		if (!isAdopter && !petAccess.canManage(application.getPet(), actor)) {
			throw new AccessDeniedException("Esta candidatura não é sua nem de um pet que você administra");
		}

		return application;
	}

	/**
	 * Aprova a candidatura e fecha o ciclo do pet numa transação só: registra a
	 * adoção, marca o pet como adotado e recusa as demais candidaturas
	 * pendentes.
	 *
	 * <p>Deixar essas três coisas para chamadas separadas abriria a janela em
	 * que o pet aparece adotado enquanto outras pessoas ainda esperam resposta —
	 * ou pior, duas aprovações para o mesmo animal.
	 */
	@Transactional
	public AdoptionApplication approve(Long id, String note, String actorEmail) {
		AdoptionApplication application = find(id);
		User actor = users.getByEmail(actorEmail);

		petAccess.requireCanManage(application.getPet(), actor);
		requirePending(application);

		application.decide(ApplicationStatus.APPROVED, actor, note);

		Pet pet = application.getPet();
		pet.setStatus(PetStatus.ADOPTED);
		pets.save(pet);

		Adoption adoption = new Adoption();
		adoption.setApplication(application);
		adoption.setPet(pet);
		adoption.setAdopter(application.getAdopter());
		adoption.setAdoptedOn(LocalDate.now());
		adoptions.save(adoption);

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

	/** Desistência do próprio candidato — libera a vaga dele para outro pet. */
	@Transactional
	public AdoptionApplication cancel(Long id, String actorEmail) {
		AdoptionApplication application = find(id);
		User actor = users.getByEmail(actorEmail);

		if (!application.getAdopter().getId().equals(actor.getId())) {
			throw new AccessDeniedException("Só quem se candidatou pode desistir da candidatura");
		}
		requirePending(application);

		application.decide(ApplicationStatus.CANCELED, actor, null);
		return applications.save(application);
	}

	// ======================================================== apoio ==========

	private void rejectRemainingCandidates(AdoptionApplication approved, User decidedBy) {
		List<AdoptionApplication> stillPending = applications
				.findByPet_IdAndStatus(approved.getPet().getId(), ApplicationStatus.PENDING);

		for (AdoptionApplication other : stillPending) {
			if (other.getId().equals(approved.getId())) {
				continue;
			}
			other.decide(ApplicationStatus.REJECTED, decidedBy,
					"Outra candidatura foi aprovada para este pet.");
		}

		applications.saveAll(stillPending);
	}

	private AdoptionApplication find(Long id) {
		return applications.findById(id).orElseThrow(() -> NotFoundException.of("Candidatura", id));
	}

	private void requirePending(AdoptionApplication application) {
		if (application.getStatus().isFinal()) {
			throw new ConflictException(
					"Esta candidatura já foi decidida (" + application.getStatus() + ").");
		}
	}
}
