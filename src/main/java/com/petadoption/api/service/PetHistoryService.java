package com.petadoption.api.service;

import com.petadoption.api.domain.HealthRecordKind;
import com.petadoption.api.domain.Organization;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetHealthRecord;
import com.petadoption.api.domain.PetStay;
import com.petadoption.api.domain.StayKind;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.OrganizationRepository;
import com.petadoption.api.repository.PetHealthRecordRepository;
import com.petadoption.api.repository.PetRepository;
import com.petadoption.api.repository.PetStayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Linha do tempo do animal: onde esteve, com quem, e sua história de saúde.
 */
@Service
public class PetHistoryService {

	private final PetStayRepository stays;
	private final PetHealthRecordRepository healthRecords;
	private final PetRepository pets;
	private final OrganizationRepository organizations;
	private final UserService users;
	private final PetAccess petAccess;
	private final OrganizationAccess organizationAccess;

	public PetHistoryService(PetStayRepository stays, PetHealthRecordRepository healthRecords, PetRepository pets,
			OrganizationRepository organizations, UserService users, PetAccess petAccess,
			OrganizationAccess organizationAccess) {
		this.stays = stays;
		this.healthRecords = healthRecords;
		this.pets = pets;
		this.organizations = organizations;
		this.users = users;
		this.petAccess = petAccess;
		this.organizationAccess = organizationAccess;
	}

	public record StayData(
			StayKind kind,
			String location,
			LocalDate startedOn,
			String notes,
			Long custodianOrganizationId) {
	}

	public record HealthData(HealthRecordKind kind, LocalDate occurredOn, String description) {
	}

	// ================================================= rastreabilidade =======

	/**
	 * Registra uma nova permanência, encerrando a anterior na data em que esta
	 * começa.
	 *
	 * <p>A linha do tempo não tem buracos nem sobreposições porque o encerramento
	 * é consequência da abertura, e não uma segunda chamada que alguém pode
	 * esquecer de fazer. O banco reforça com índice único parcial: no máximo uma
	 * permanência aberta por animal.
	 */
	@Transactional
	public PetStay addStay(Long petId, StayData data, String actorEmail) {
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));
		User actor = users.getByEmail(actorEmail);
		petAccess.requireCanManage(pet, actor);

		Organization custodianOrg = null;
		User custodianUser = null;

		if (data.custodianOrganizationId() != null) {
			custodianOrg = organizations.findById(data.custodianOrganizationId())
					.orElseThrow(() -> NotFoundException.of("Organização", data.custodianOrganizationId()));
			// Só se registra guarda de uma organização da qual se faz parte.
			organizationAccess.requireMember(custodianOrg.getId(), actor);
		}
		else if (pet.isOwnedByOrganization()) {
			custodianOrg = pet.getOwnerOrg();
		}
		else {
			custodianUser = pet.getOwnerUser();
		}

		return openStay(pet, data.kind(), data.location(), data.startedOn(), data.notes(),
				custodianUser, custodianOrg);
	}

	/**
	 * Abre uma permanência encerrando a anterior. Sem verificação de permissão:
	 * é chamada por serviços que já validaram o direito de agir — a aprovação de
	 * adoção, por exemplo.
	 */
	@Transactional
	public PetStay openStay(Pet pet, StayKind kind, String location, LocalDate startedOn, String notes,
			User custodianUser, Organization custodianOrg) {

		LocalDate start = startedOn != null ? startedOn : LocalDate.now();

		stays.findByPet_IdAndEndedOnIsNull(pet.getId()).ifPresent(current -> {
			if (start.isBefore(current.getStartedOn())) {
				throw new ConflictException("A nova permanência começa antes da permanência atual ("
						+ current.getStartedOn() + ").");
			}
			current.setEndedOn(start);
			// Flush explícito, e não save(). No flush automático o Hibernate
			// ordena todos os INSERTs antes dos UPDATEs, então a permanência
			// nova entraria com a anterior ainda aberta — e o índice único
			// parcial recusaria, com razão. Fechar antes de abrir é a ordem
			// correta também conceitualmente.
			stays.saveAndFlush(current);
		});

		PetStay stay = new PetStay();
		stay.setPet(pet);
		stay.setKind(kind);
		stay.setLocation(location);
		stay.setStartedOn(start);
		stay.setNotes(notes);
		stay.setCustodianUser(custodianUser);
		stay.setCustodianOrg(custodianOrg);
		return stays.save(stay);
	}

	/** Histórico completo, do resgate para frente. */
	@Transactional(readOnly = true)
	public List<PetStay> historyOf(Long petId) {
		if (!pets.existsById(petId)) {
			throw NotFoundException.of("Pet", petId);
		}
		return stays.findByPet_IdOrderByStartedOnAscIdAsc(petId);
	}

	// ========================================================== saúde ========

	@Transactional
	public PetHealthRecord addHealthRecord(Long petId, HealthData data, String actorEmail) {
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));
		User actor = users.getByEmail(actorEmail);
		petAccess.requireCanManage(pet, actor);

		if (data.occurredOn().isAfter(LocalDate.now())) {
			throw new ConflictException("Um evento de saúde não acontece no futuro.");
		}

		PetHealthRecord record = new PetHealthRecord();
		record.setPet(pet);
		record.setKind(data.kind());
		record.setOccurredOn(data.occurredOn());
		record.setDescription(data.description());
		record.setRecordedBy(actor);
		return healthRecords.save(record);
	}

	/**
	 * Diferente do histórico de localização, a ficha de saúde não é pública:
	 * doença e tratamento são detalhe clínico, e o catálogo já mostra o resumo
	 * que interessa a quem procura adotar.
	 */
	@Transactional(readOnly = true)
	public List<PetHealthRecord> healthRecordsOf(Long petId, String actorEmail) {
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));
		petAccess.requireCanManage(pet, users.getByEmail(actorEmail));

		return healthRecords.findByPet_IdOrderByOccurredOnDescIdDesc(petId);
	}
}
