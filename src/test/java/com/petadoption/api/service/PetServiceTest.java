package com.petadoption.api.service;

import com.petadoption.api.domain.Organization;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetSex;
import com.petadoption.api.domain.PetSize;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.domain.User;
import com.petadoption.api.repository.OrganizationRepository;
import com.petadoption.api.repository.PetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras de pet em isolamento. O que exige a cadeia HTTP inteira — códigos de
 * status, token, filtros — está em PetIntegrationTest.
 */
class PetServiceTest {

	private PetRepository pets;
	private OrganizationRepository organizations;
	private UserService users;
	private OrganizationAccess access;
	private PetService service;

	private User actor;

	@BeforeEach
	void setUp() {
		pets = mock(PetRepository.class);
		organizations = mock(OrganizationRepository.class);
		users = mock(UserService.class);
		access = mock(OrganizationAccess.class);
		service = new PetService(pets, organizations, users, access);

		actor = new User();
		actor.setId(1L);
		actor.setEmail("ator@example.com");

		when(users.getByEmail("ator@example.com")).thenReturn(actor);
		when(pets.save(any(Pet.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("sem organização informada, o dono é quem está autenticado")
	void createsPetOwnedByActor() {
		Pet pet = service.create(data("Luna", null), null, "ator@example.com");

		assertThat(pet.getOwnerUser()).isSameAs(actor);
		assertThat(pet.getOwnerOrg()).isNull();
		assertThat(pet.getStatus()).isEqualTo(PetStatus.AVAILABLE);
	}

	@Test
	@DisplayName("espécie é normalizada para forma canônica")
	void normalizesSpecies() {
		Pet pet = service.create(data("Mia", null), null, "ator@example.com");

		assertThat(pet.getSpecies()).isEqualTo("CAT");
	}

	@Test
	@DisplayName("cadastrar para organização exige vínculo, verificado antes de salvar")
	void createsPetForOrganizationOnlyWithMembership() {
		Organization organization = new Organization();
		organization.setName("Abrigo");
		when(organizations.findById(7L)).thenReturn(Optional.of(organization));

		Pet pet = service.create(data("Thor", null), 7L, "ator@example.com");

		verify(access).requireMember(7L, actor);
		assertThat(pet.getOwnerOrg()).isSameAs(organization);
		assertThat(pet.getOwnerUser()).isNull();
	}

	@Test
	@DisplayName("organização inexistente devolve não encontrado antes de checar vínculo")
	void createFailsForUnknownOrganization() {
		when(organizations.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(data("Fantasma", null), 99L, "ator@example.com"))
				.isInstanceOf(NotFoundException.class);

		verify(pets, never()).save(any(Pet.class));
	}

	@Test
	@DisplayName("pet de outra pessoa não pode ser editado")
	void rejectsUpdateFromNonOwner() {
		User someoneElse = new User();
		someoneElse.setId(42L);

		Pet pet = new Pet();
		pet.setOwnerUser(someoneElse);
		when(pets.findById(5L)).thenReturn(Optional.of(pet));

		assertThatThrownBy(() -> service.update(5L, data("Roubada", null), "ator@example.com"))
				.isInstanceOf(AccessDeniedException.class);

		verify(pets, never()).save(any(Pet.class));
	}

	@Test
	@DisplayName("estado terminal não admite transição de volta")
	void rejectsTransitionOutOfTerminalStatus() {
		Pet pet = new Pet();
		pet.setOwnerUser(actor);
		pet.setStatus(PetStatus.DECEASED);
		when(pets.findById(8L)).thenReturn(Optional.of(pet));

		assertThatThrownBy(() -> service.update(8L, data("Rex", PetStatus.AVAILABLE), "ator@example.com"))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	@DisplayName("editar sem informar status preserva o status atual")
	void keepsStatusWhenNotInformed() {
		Pet pet = new Pet();
		pet.setOwnerUser(actor);
		pet.setStatus(PetStatus.ADOPTED);
		when(pets.findById(9L)).thenReturn(Optional.of(pet));

		Pet updated = service.update(9L, data("Bidu", null), "ator@example.com");

		assertThat(updated.getStatus()).isEqualTo(PetStatus.ADOPTED);
	}

	private PetService.PetData data(String name, PetStatus status) {
		return new PetService.PetData(name, "cat", null, PetSex.FEMALE, PetSize.SMALL, null, null, status,
				false, false, false, false, null, null);
	}
}
