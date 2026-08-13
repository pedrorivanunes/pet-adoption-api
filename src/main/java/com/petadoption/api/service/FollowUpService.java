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
 * Acompanhamento pós-adoção: registro de contatos e o relatório do período.
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
	 * Relatório do acompanhamento.
	 *
	 * @param monthsWithoutContact meses da janela sem nenhum contato registrado —
	 *                             é a informação que denuncia acompanhamento que
	 *                             parou pela metade, coisa que uma contagem total
	 *                             de visitas esconde
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
			throw new ConflictException("O contato não pode ser anterior à data da adoção ("
					+ adoption.getAdoptedOn() + ").");
		}
		if (data.occurredOn().isAfter(LocalDate.now())) {
			throw new ConflictException("O contato não pode estar no futuro.");
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

		// A ficha de saúde é do animal para a vida toda; o relatório recorta
		// apenas a janela de acompanhamento.
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

	// ======================================================== apoio ==========

	/**
	 * Meses já decorridos da janela em que não houve nenhum contato. Meses que
	 * ainda não chegaram não contam como falha — não se cobra acompanhamento do
	 * futuro.
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
						"Este pet não tem adoção registrada, então não há acompanhamento."));
	}

	/**
	 * O dever de acompanhar é de quem entregou o animal — o abrigo ou o tutor
	 * anterior. Depois da adoção a tutoria passa ao adotante, então a permissão
	 * de registrar não pode vir de administrar o pet: seria dar ao adotante a
	 * caneta para atestar o próprio acompanhamento.
	 */
	private void requireResponsible(Adoption adoption, User actor) {
		if (!isResponsible(adoption, actor)) {
			throw new AccessDeniedException(
					"O acompanhamento é responsabilidade de quem entregou o animal");
		}
	}

	/** Ler o relatório cabe também ao adotante: é sobre ele e sobre o animal dele. */
	private void requireParticipant(Adoption adoption, User actor) {
		boolean isAdopter = adoption.getAdopter().getId().equals(actor.getId());
		if (!isAdopter && !isResponsible(adoption, actor)) {
			throw new AccessDeniedException("Este acompanhamento não é seu");
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
