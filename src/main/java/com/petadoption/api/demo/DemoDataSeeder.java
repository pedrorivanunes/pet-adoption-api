package com.petadoption.api.demo;

import com.petadoption.api.domain.Adoption;
import com.petadoption.api.domain.AdoptionApplication;
import com.petadoption.api.domain.FollowUpKind;
import com.petadoption.api.domain.HealthRecordKind;
import com.petadoption.api.domain.HousingType;
import com.petadoption.api.domain.OrgMemberRole;
import com.petadoption.api.domain.Organization;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.PetStay;
import com.petadoption.api.domain.StayKind;
import com.petadoption.api.repository.AdoptionRepository;
import com.petadoption.api.repository.PetStayRepository;
import com.petadoption.api.repository.UserRepository;
import com.petadoption.api.service.AdopterProfileService;
import com.petadoption.api.service.AdoptionApplicationService;
import com.petadoption.api.service.FollowUpService;
import com.petadoption.api.service.OrganizationService;
import com.petadoption.api.service.PetHistoryService;
import com.petadoption.api.service.PetService;
import com.petadoption.api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Popula a base com um cenário completo, para que o projeto seja explorável em
 * poucos minutos sem precisar montar dados na mão.
 *
 * <p>Ativo apenas no perfil {@code demo}. E, deliberadamente, tudo é criado
 * <strong>pelos serviços da aplicação</strong>, não por INSERTs: dado semeado
 * assim não consegue existir em estado que a API recusaria. Um seed em SQL
 * puro pode criar exatamente a inconsistência que as regras impedem, e aí a
 * demonstração passa a mostrar algo que o sistema não produz.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	/** Igual para todas as contas de exemplo — está documentado no README. */
	public static final String PASSWORD = "senha-de-demonstracao";

	private final UserRepository users;
	private final UserService userService;
	private final OrganizationService organizations;
	private final PetService pets;
	private final PetHistoryService history;
	private final AdopterProfileService profiles;
	private final AdoptionApplicationService applications;
	private final FollowUpService followUps;
	private final AdoptionRepository adoptions;
	private final PetStayRepository stays;

	public DemoDataSeeder(UserRepository users, UserService userService, OrganizationService organizations,
			PetService pets, PetHistoryService history, AdopterProfileService profiles,
			AdoptionApplicationService applications, FollowUpService followUps, AdoptionRepository adoptions,
			PetStayRepository stays) {
		this.users = users;
		this.userService = userService;
		this.organizations = organizations;
		this.pets = pets;
		this.history = history;
		this.profiles = profiles;
		this.applications = applications;
		this.followUps = followUps;
		this.adoptions = adoptions;
		this.stays = stays;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.count() > 0) {
			log.info("Base já contém dados; seed de demonstração ignorado.");
			return;
		}

		log.info("Perfil 'demo' ativo — semeando cenário de exemplo.");

		// ---------------------------------------------------------- pessoas --
		String ana = register("Ana Ribeiro", "ana@exemplo.br", "51999990001");
		String bruno = register("Bruno Machado", "bruno@exemplo.br", "51999990002");
		String carla = register("Carla Souza", "carla@exemplo.br", "51999990003");
		String diego = register("Diego Alves", "diego@exemplo.br", "51999990004");
		String elisa = register("Elisa Prado", "elisa@exemplo.br", "51999990005");

		// ----------------------------------------------------- organização ---
		Organization abrigo = organizations.create(new OrganizationService.OrganizationData(
				"Abrigo Quatro Patas",
				"Abrigo comunitário de Porto Alegre, atuando desde 2015.",
				"contato@quatropatas.exemplo.br",
				"5133330000",
				"Rua das Acácias, 120 — Porto Alegre/RS"), ana);

		organizations.addMember(abrigo.getId(), "bruno@exemplo.br", OrgMemberRole.STAFF, ana);

		// --------------------------------------------------------- animais ---
		// Jovem, saudável e sociável — o caso fácil.
		Pet luna = shelterPet(abrigo.getId(), ana, "Luna", "dog", "Vira-lata", PetSex.FEMALE, PetSize.MEDIUM,
				LocalDate.now().minusYears(3), new Health(false, false, false), true);

		// Idoso com tratamento contínuo: só combina com quem tem tempo.
		Pet thor = shelterPet(abrigo.getId(), ana, "Thor", "dog", "Pastor alemão", PetSex.MALE, PetSize.LARGE,
				LocalDate.now().minusYears(7), new Health(true, true, true), true);

		// Doença crônica e não convive com outros animais: dois filtros de uma vez.
		Pet mia = shelterPet(abrigo.getId(), ana, "Mia", "cat", null, PetSex.FEMALE, PetSize.SMALL,
				LocalDate.now().minusYears(2), new Health(false, true, false), false);

		Pet bidu = shelterPet(abrigo.getId(), ana, "Bidu", "dog", "Poodle", PetSex.MALE, PetSize.SMALL,
				LocalDate.now().minusYears(5), new Health(false, false, false), true);

		// Pet de tutora particular, para o catálogo não ser só de abrigo.
		pets.create(new PetService.PetData("Nina", "cat", "Siamês", PetSex.FEMALE, PetSize.MEDIUM,
				LocalDate.now().minusYears(1), true, null,
				false, false, false, false, true, "Saudável, castrada."), null, elisa);

		// -------------------------------------------------- rastreabilidade --
		rescueThenShelter(luna.getId(), abrigo.getId(), ana, "Avenida Ipiranga, Porto Alegre", 14);
		rescueThenShelter(thor.getId(), abrigo.getId(), ana, "Parque Farroupilha, Porto Alegre", 8);
		rescueThenShelter(mia.getId(), abrigo.getId(), ana, "Rua General Lima e Silva, Porto Alegre", 5);
		rescueThenShelter(bidu.getId(), abrigo.getId(), ana, "Entregue por antigo tutor", 6);

		history.addHealthRecord(luna.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.VACCINATION, LocalDate.now().minusMonths(11), "V10, dose única"), ana);
		history.addHealthRecord(thor.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.TREATMENT, LocalDate.now().minusMonths(2),
				"Displasia coxofemoral — fisioterapia semanal"), ana);
		history.addHealthRecord(mia.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.ILLNESS, LocalDate.now().minusMonths(4),
				"Insuficiência renal crônica, tratada com dieta específica"), ana);

		// ------------------------------------------------------- adotantes ---
		// Carla procura exatamente o perfil da Luna.
		profiles.save(new AdopterProfileService.ProfileData(
				HousingType.HOUSE, false, 2, false, true,
				"dog", "Vira-lata", PetSize.MEDIUM, PetSex.FEMALE,
				false, false, false), carla);

		// Diego já tem animais e aceita cuidados especiais.
		profiles.save(new AdopterProfileService.ProfileData(
				HousingType.APARTMENT, true, 3, true, true,
				"dog", null, PetSize.SMALL, PetSex.MALE,
				true, true, false), diego);

		// -------------------------------------------- candidatura pendente ---
		applications.apply(luna.getId(), "Tenho pátio grande e trabalho de casa.", carla);

		// ------------------------------------------- adoção já concretizada --
		AdoptionApplication aprovada = applications.apply(
				bidu.getId(), "Meu apartamento é tranquilo e já tenho uma cadela idosa.", diego);
		applications.approve(aprovada.getId(), "Perfil combina bem com o Bidu.", ana);

		LocalDate adoptedOn = LocalDate.now().minusMonths(4);
		backdateAdoption(bidu.getId(), adoptedOn);

		followUps.record(bidu.getId(), new FollowUpService.FollowUpData(
				FollowUpKind.VISIT, adoptedOn.plusDays(15), "Visita inicial: adaptação tranquila."), ana);
		followUps.record(bidu.getId(), new FollowUpService.FollowUpData(
				FollowUpKind.CALL, adoptedOn.plusMonths(1), "Contato telefônico, tudo bem."), ana);
		// De propósito não há contato nos meses seguintes: o relatório precisa
		// mostrar lacuna em algum cenário para que ela seja vista funcionando.
		followUps.record(bidu.getId(), new FollowUpService.FollowUpData(
				FollowUpKind.MESSAGE, LocalDate.now().minusDays(3), "Fotos recebidas, animal saudável."), ana);

		// Depois da adoção quem cuida da ficha de saúde é o novo tutor.
		history.addHealthRecord(bidu.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.NEUTERING, adoptedOn.plusMonths(1), "Castração realizada"), diego);

		log.info("Seed concluído: 5 pessoas, 1 organização, 5 pets, 1 candidatura pendente e 1 adoção "
				+ "com acompanhamento. Senha de todas as contas: {}", PASSWORD);
	}

	// ======================================================== apoio ==========

	private String register(String name, String email, String phone) {
		userService.register(new UserService.NewUser(name, email, PASSWORD, phone));
		return email;
	}

	/** Condições de saúde de um animal do seed. */
	private record Health(boolean specialNeedsAndTreatment, boolean chronicDisease, boolean constantCare) {
	}

	private Pet shelterPet(Long orgId, String actor, String name, String species, String breed, PetSex sex,
			PetSize size, LocalDate birthDate, Health health, boolean goodWithOthers) {

		return pets.create(new PetService.PetData(name, species, breed, sex, size, birthDate, true, null,
				health.specialNeedsAndTreatment(), health.specialNeedsAndTreatment(), health.chronicDisease(),
				health.constantCare(), goodWithOthers, null), orgId, actor);
	}

	/** Resgate na rua e, meses depois, entrada no abrigo. */
	private void rescueThenShelter(Long petId, Long orgId, String actor, String where, int monthsAgo) {
		history.addStay(petId, new PetHistoryService.StayData(StayKind.RESCUE, where,
				LocalDate.now().minusMonths(monthsAgo), "Resgatado em situação de rua", null), actor);

		history.addStay(petId, new PetHistoryService.StayData(StayKind.SHELTER,
				"Abrigo Quatro Patas — Porto Alegre/RS",
				LocalDate.now().minusMonths(monthsAgo).plusDays(20), null, orgId), actor);
	}

	/**
	 * Recua a data da adoção e da permanência correspondente.
	 *
	 * <p>Único ponto do seed que escreve direto no repositório: a aprovação
	 * carimba a data de hoje, e uma adoção de hoje deixaria o relatório de
	 * acompanhamento sem nada para mostrar. É viagem no tempo de demonstração,
	 * não regra de negócio — por isso está isolada aqui e não no serviço.
	 */
	private void backdateAdoption(Long petId, LocalDate adoptedOn) {
		Adoption adoption = adoptions.findFirstByPet_IdOrderByAdoptedOnDescIdDesc(petId).orElseThrow();
		adoption.setAdoptedOn(adoptedOn);
		adoptions.save(adoption);

		// A permanência no abrigo foi encerrada hoje pela aprovação; recuar só a
		// adoção deixaria a linha do tempo com um salto. As duas pontas andam
		// juntas.
		LocalDate today = LocalDate.now();
		List<PetStay> timeline = stays.findByPet_IdOrderByStartedOnAscIdAsc(petId);
		for (PetStay stay : timeline) {
			if (stay.isOpen()) {
				stay.setStartedOn(adoptedOn);
			}
			else if (today.equals(stay.getEndedOn())) {
				stay.setEndedOn(adoptedOn);
			}
		}
		stays.saveAll(timeline);
	}
}
