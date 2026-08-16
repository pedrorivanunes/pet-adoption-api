package com.petadoption.api.service;

import com.petadoption.api.domain.Adoption;
import com.petadoption.api.domain.AdoptionFollowUp;
import com.petadoption.api.domain.FollowUpKind;
import com.petadoption.api.domain.PetHealthRecord;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.AdoptionFollowUpRepository;
import com.petadoption.api.repository.AdoptionRepository;
import com.petadoption.api.repository.PetHealthRecordRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Post-adoption follow-up: logging contacts and reporting on the period.
 */
@Service
public class FollowUpService {

	private final AdoptionFollowUpRepository followUps;
	private final AdoptionRepository adoptions;
	private final PetHealthRecordRepository healthRecords;
	private final UserService users;
	private final OrganizationAccess organizationAccess;

	public FollowUpService(AdoptionFollowUpRepository followUps, AdoptionRepository adoptions,
			PetHealthRecordRepository healthRecords, UserService users, OrganizationAccess organizationAccess) {
		this.followUps = followUps;
		this.adoptions = adoptions;
		this.healthRecords = healthRecords;
		this.users = users;
		this.organizationAccess = organizationAccess;
	}

	public record FollowUpData(FollowUpKind kind, LocalDate occurredOn, String notes) {
	}

	/**
	 * The follow-up report.
	 *
	 * @param monthsWithoutContact months of the window with no contact logged --
	 *                             this is what exposes a follow-up that stopped
	 *                             halfway, something a total visit count hides
	 */
	public record Report(
			Adoption adoption,
			LocalDate windowEndsOn,
			boolean minimumPeriodComplete,
			long daysSinceAdoption,
			List<AdoptionFollowUp> interactions,
			List<PetHealthRecord> healthRecords,
			List<YearMonth> monthsWithoutContact) {
	}

	@Transactional
	public AdoptionFollowUp record(Long petId, FollowUpData data, String actorEmail) {
		Adoption adoption = findByPet(petId);
		User actor = users.getByEmail(actorEmail);
		requireResponsible(adoption, actor);

		if (data.occurredOn().isBefore(adoption.getAdoptedOn())) {
			throw new ConflictException("The contact cannot predate the adoption date ("
					+ adoption.getAdoptedOn() + ").");
		}
		if (data.occurredOn().isAfter(LocalDate.now())) {
			throw new ConflictException("The contact cannot be in the future.");
		}

		AdoptionFollowUp followUp = new AdoptionFollowUp();
		followUp.setAdoption(adoption);
		followUp.setKind(data.kind());
		followUp.setOccurredOn(data.occurredOn());
		followUp.setNotes(data.notes());
		followUp.setRecordedBy(actor);
		return followUps.save(followUp);
	}

	@Transactional(readOnly = true)
	public Report report(Long petId, String actorEmail) {
		Adoption adoption = findByPet(petId);
		requireParticipant(adoption, users.getByEmail(actorEmail));

		LocalDate adoptedOn = adoption.getAdoptedOn();
		LocalDate windowEndsOn = adoption.followUpDeadline();
		LocalDate today = LocalDate.now();

		List<AdoptionFollowUp> interactions =
				followUps.findByAdoption_IdOrderByOccurredOnAscIdAsc(adoption.getId());

		// The health record covers the animal's whole life; the report crops it
		// to the follow-up window only.
		List<PetHealthRecord> health = healthRecords
				.findByPet_IdAndOccurredOnBetweenOrderByOccurredOnAsc(
						adoption.getPet().getId(), adoptedOn, windowEndsOn);

		return new Report(
				adoption,
				windowEndsOn,
				!today.isBefore(windowEndsOn),
				ChronoUnit.DAYS.between(adoptedOn, today),
				interactions,
				health,
				monthsWithoutContact(adoptedOn, windowEndsOn, today, interactions));
	}

	// ======================================================= helpers =========

	/**
	 * Months of the window that have already passed with no contact at all.
	 * Months that have not arrived yet do not count as a failure -- nobody owes
	 * follow-up for the future.
	 */
	private List<YearMonth> monthsWithoutContact(LocalDate adoptedOn, LocalDate windowEndsOn, LocalDate today,
			List<AdoptionFollowUp> interactions) {

		Set<YearMonth> monthsWithContact = interactions.stream()
				.map(followUp -> YearMonth.from(followUp.getOccurredOn()))
				.collect(Collectors.toSet());

		YearMonth last = YearMonth.from(today.isBefore(windowEndsOn) ? today : windowEndsOn.minusDays(1));
		List<YearMonth> gaps = new ArrayList<>();

		for (YearMonth month = YearMonth.from(adoptedOn); !month.isAfter(last); month = month.plusMonths(1)) {
			if (!monthsWithContact.contains(month)) {
				gaps.add(month);
			}
		}
		return gaps;
	}

	private Adoption findByPet(Long petId) {
		return adoptions.findFirstByPet_IdOrderByAdoptedOnDescIdDesc(petId)
				.orElseThrow(() -> new NotFoundException(
						"This pet has no adoption on record, so there is no follow-up."));
	}

	/**
	 * The duty to follow up belongs to whoever handed the animal over -- the
	 * shelter or the previous guardian. After the adoption, guardianship passes
	 * to the adopter, so permission to log contacts cannot come from managing
	 * the pet: that would hand the adopter the pen to certify their own
	 * follow-up.
	 */
	private void requireResponsible(Adoption adoption, User actor) {
		if (!isResponsible(adoption, actor)) {
			throw new AccessDeniedException(
					"The follow-up is the responsibility of whoever handed the animal over");
		}
	}

	/** Reading the report is also the adopter's right: it is about them and their animal. */
	private void requireParticipant(Adoption adoption, User actor) {
		boolean isAdopter = adoption.getAdopter().getId().equals(actor.getId());
		if (!isAdopter && !isResponsible(adoption, actor)) {
			throw new AccessDeniedException("This follow-up is not yours");
		}
	}

	private boolean isResponsible(Adoption adoption, User actor) {
		if (adoption.getOriginUser() != null) {
			return adoption.getOriginUser().getId().equals(actor.getId());
		}
		if (adoption.getOriginOrg() != null) {
			return organizationAccess.isMember(adoption.getOriginOrg().getId(), actor);
		}
		return false;
	}
}
