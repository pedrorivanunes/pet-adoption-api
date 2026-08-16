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
 * Fills the database with a complete scenario, so the project can be explored in
 * a few minutes without assembling data by hand.
 *
 * <p>Active only under the {@code demo} profile. And, deliberately, everything
 * is created <strong>through the application services</strong>, not through
 * INSERTs: data seeded this way cannot exist in a state the API would reject. A
 * seed written in raw SQL can create exactly the inconsistency the rules
 * prevent, and then the demo shows something the system does not produce.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	/** The same for every sample account -- it is documented in the README. */
	public static final String PASSWORD = "demo-password";

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
			log.info("Database already contains data; demo seed skipped.");
			return;
		}

		log.info("Profile 'demo' active -- seeding the sample scenario.");

		// ----------------------------------------------------------- people --
		String ana = register("Ana Ribeiro", "ana@example.com", "51999990001");
		String bruno = register("Bruno Machado", "bruno@example.com", "51999990002");
		String carla = register("Carla Souza", "carla@example.com", "51999990003");
		String diego = register("Diego Alves", "diego@example.com", "51999990004");
		String elisa = register("Elisa Prado", "elisa@example.com", "51999990005");

		// ---------------------------------------------------- organization ---
		Organization shelter = organizations.create(new OrganizationService.OrganizationData(
				"Four Paws Shelter",
				"Community shelter in Porto Alegre, running since 2015.",
				"contact@fourpaws.example.com",
				"5133330000",
				"Rua das Acacias 120, Porto Alegre/RS"), ana);

		organizations.addMember(shelter.getId(), "bruno@example.com", OrgMemberRole.STAFF, ana);

		// -------------------------------------------------------- animals ---
		// Young, healthy and sociable -- the easy case.
		Pet luna = shelterPet(shelter.getId(), ana, "Luna", "dog", "Mixed breed", PetSex.FEMALE, PetSize.MEDIUM,
				LocalDate.now().minusYears(3), new Health(false, false, false), true);

		// Elderly, on continuous treatment: only a match for someone with time.
		Pet thor = shelterPet(shelter.getId(), ana, "Thor", "dog", "German shepherd", PetSex.MALE, PetSize.LARGE,
				LocalDate.now().minusYears(7), new Health(true, true, true), true);

		// Chronic illness and does not get along with other animals: two filters at once.
		Pet mia = shelterPet(shelter.getId(), ana, "Mia", "cat", null, PetSex.FEMALE, PetSize.SMALL,
				LocalDate.now().minusYears(2), new Health(false, true, false), false);

		Pet bidu = shelterPet(shelter.getId(), ana, "Bidu", "dog", "Poodle", PetSex.MALE, PetSize.SMALL,
				LocalDate.now().minusYears(5), new Health(false, false, false), true);

		// A private guardian's pet, so the catalogue is not shelter-only.
		pets.create(new PetService.PetData("Nina", "cat", "Siamese", PetSex.FEMALE, PetSize.MEDIUM,
				LocalDate.now().minusYears(1), true, null,
				false, false, false, false, true, "Healthy, neutered."), null, elisa);

		// ------------------------------------------------------ traceability --
		rescueThenShelter(luna.getId(), shelter.getId(), ana, "Avenida Ipiranga, Porto Alegre", 14);
		rescueThenShelter(thor.getId(), shelter.getId(), ana, "Parque Farroupilha, Porto Alegre", 8);
		rescueThenShelter(mia.getId(), shelter.getId(), ana, "Rua General Lima e Silva, Porto Alegre", 5);
		rescueThenShelter(bidu.getId(), shelter.getId(), ana, "Handed over by a previous guardian", 6);

		history.addHealthRecord(luna.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.VACCINATION, LocalDate.now().minusMonths(11), "DHPP, single dose"), ana);
		history.addHealthRecord(thor.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.TREATMENT, LocalDate.now().minusMonths(2),
				"Hip dysplasia -- weekly physiotherapy"), ana);
		history.addHealthRecord(mia.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.ILLNESS, LocalDate.now().minusMonths(4),
				"Chronic kidney failure, managed with a specific diet"), ana);

		// ------------------------------------------------------- adopters ---
		// Carla is looking for exactly Luna's profile.
		profiles.save(new AdopterProfileService.ProfileData(
				HousingType.HOUSE, false, 2, false, true,
				"dog", "Mixed breed", PetSize.MEDIUM, PetSex.FEMALE,
				false, false, false), carla);

		// Diego already has animals and accepts special care.
		profiles.save(new AdopterProfileService.ProfileData(
				HousingType.APARTMENT, true, 3, true, true,
				"dog", null, PetSize.SMALL, PetSex.MALE,
				true, true, false), diego);

		// ------------------------------------------- pending application ---
		applications.apply(luna.getId(), "I have a large yard and I work from home.", carla);

		// -------------------------------------------- adoption already done --
		AdoptionApplication approved = applications.apply(
				bidu.getId(), "My flat is quiet and I already have an elderly dog.", diego);
		applications.approve(approved.getId(), "The profile is a good fit for Bidu.", ana);

		LocalDate adoptedOn = LocalDate.now().minusMonths(4);
		backdateAdoption(bidu.getId(), adoptedOn);

		followUps.record(bidu.getId(), new FollowUpService.FollowUpData(
				FollowUpKind.VISIT, adoptedOn.plusDays(15), "First visit: settling in well."), ana);
		followUps.record(bidu.getId(), new FollowUpService.FollowUpData(
				FollowUpKind.CALL, adoptedOn.plusMonths(1), "Phone call, all well."), ana);
		// Deliberately no contact over the following months: the report needs a
		// scenario with a gap for that part of it to be seen working.
		followUps.record(bidu.getId(), new FollowUpService.FollowUpData(
				FollowUpKind.MESSAGE, LocalDate.now().minusDays(3), "Photos received, animal healthy."), ana);

		// After the adoption, the health record is the new guardian's to keep.
		history.addHealthRecord(bidu.getId(), new PetHistoryService.HealthData(
				HealthRecordKind.NEUTERING, adoptedOn.plusMonths(1), "Neutering carried out"), diego);

		log.info("Seed complete: 5 people, 1 organization, 5 pets, 1 pending application and 1 adoption "
				+ "with follow-up. Password for every account: {}", PASSWORD);
	}

	// ======================================================= helpers =========

	private String register(String name, String email, String phone) {
		userService.register(new UserService.NewUser(name, email, PASSWORD, phone));
		return email;
	}

	/** Health conditions of one seeded animal. */
	private record Health(boolean specialNeedsAndTreatment, boolean chronicDisease, boolean constantCare) {
	}

	private Pet shelterPet(Long orgId, String actor, String name, String species, String breed, PetSex sex,
			PetSize size, LocalDate birthDate, Health health, boolean goodWithOthers) {

		return pets.create(new PetService.PetData(name, species, breed, sex, size, birthDate, true, null,
				health.specialNeedsAndTreatment(), health.specialNeedsAndTreatment(), health.chronicDisease(),
				health.constantCare(), goodWithOthers, null), orgId, actor);
	}

	/** A street rescue and, months later, arrival at the shelter. */
	private void rescueThenShelter(Long petId, Long orgId, String actor, String where, int monthsAgo) {
		history.addStay(petId, new PetHistoryService.StayData(StayKind.RESCUE, where,
				LocalDate.now().minusMonths(monthsAgo), "Rescued off the street", null), actor);

		history.addStay(petId, new PetHistoryService.StayData(StayKind.SHELTER,
				"Four Paws Shelter -- Porto Alegre/RS",
				LocalDate.now().minusMonths(monthsAgo).plusDays(20), null, orgId), actor);
	}

	/**
	 * Backdates the adoption and its matching stay.
	 *
	 * <p>The only place in the seed that writes straight to the repository:
	 * approval stamps today's date, and an adoption dated today would leave the
	 * follow-up report with nothing to show. This is demo time travel, not a
	 * business rule -- which is why it is isolated here and not in the service.
	 */
	private void backdateAdoption(Long petId, LocalDate adoptedOn) {
		Adoption adoption = adoptions.findFirstByPet_IdOrderByAdoptedOnDescIdDesc(petId).orElseThrow();
		adoption.setAdoptedOn(adoptedOn);
		adoptions.save(adoption);

		// The shelter stay was closed today by the approval; backdating only the
		// adoption would leave a jump in the timeline. Both ends move together.
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
