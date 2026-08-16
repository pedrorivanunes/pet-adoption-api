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
 * The animal's timeline: where it has been, with whom, and its health history.
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

	// ==================================================== traceability =======

	/**
	 * Records a new stay, closing the previous one on the date this one starts.
	 *
	 * <p>The timeline has no gaps and no overlaps because closing is a
	 * consequence of opening, not a second call someone can forget to make. The
	 * database backs this with a partial unique index: at most one open stay per
	 * animal.
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
					.orElseThrow(() -> NotFoundException.of("Organization", data.custodianOrganizationId()));
			// You can only record custody for an organization you belong to.
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
	 * Opens a stay, closing the previous one. No permission check here: it is
	 * called by services that have already validated the right to act -- the
	 * adoption approval, for instance.
	 */
	@Transactional
	public PetStay openStay(Pet pet, StayKind kind, String location, LocalDate startedOn, String notes,
			User custodianUser, Organization custodianOrg) {

		LocalDate start = startedOn != null ? startedOn : LocalDate.now();

		stays.findByPet_IdAndEndedOnIsNull(pet.getId()).ifPresent(current -> {
			if (start.isBefore(current.getStartedOn())) {
				throw new ConflictException("The new stay starts before the current stay ("
						+ current.getStartedOn() + ").");
			}
			current.setEndedOn(start);
			// An explicit flush, not save(). On automatic flush Hibernate orders
			// every INSERT before the UPDATEs, so the new stay would go in while
			// the previous one was still open -- and the partial unique index
			// would refuse, rightly. Closing before opening is also the
			// conceptually correct order.
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

	/** The full history, from the rescue onwards. */
	@Transactional(readOnly = true)
	public List<PetStay> historyOf(Long petId) {
		if (!pets.existsById(petId)) {
			throw NotFoundException.of("Pet", petId);
		}
		return stays.findByPet_IdOrderByStartedOnAscIdAsc(petId);
	}

	// ========================================================= health ========

	@Transactional
	public PetHealthRecord addHealthRecord(Long petId, HealthData data, String actorEmail) {
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));
		User actor = users.getByEmail(actorEmail);
		petAccess.requireCanManage(pet, actor);

		if (data.occurredOn().isAfter(LocalDate.now())) {
			throw new ConflictException("A health event does not happen in the future.");
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
	 * Unlike the location history, the health record is not public: illness and
	 * treatment are clinical detail, and the catalogue already shows the summary
	 * that matters to someone looking to adopt.
	 */
	@Transactional(readOnly = true)
	public List<PetHealthRecord> healthRecordsOf(Long petId, String actorEmail) {
		Pet pet = pets.findById(petId).orElseThrow(() -> NotFoundException.of("Pet", petId));
		petAccess.requireCanManage(pet, users.getByEmail(actorEmail));

		return healthRecords.findByPet_IdOrderByOccurredOnDescIdDesc(petId);
	}
}
