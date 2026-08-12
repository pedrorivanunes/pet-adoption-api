package com.petadoption.api.service;

import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Responde quem pode administrar um pet — editar, remover e decidir
 * candidaturas a ele.
 *
 * <p>Mesmo motivo de {@link OrganizationAccess} existir: a pergunta é feita por
 * mais de um serviço, e uma regra de autorização duplicada não falha em voz
 * alta quando as cópias divergem — ela só passa a permitir o que deveria negar.
 */
@Component
public class PetAccess {

	private final OrganizationAccess organizations;

	public PetAccess(OrganizationAccess organizations) {
		this.organizations = organizations;
	}

	/**
	 * Pet de pessoa: só o dono. Pet de organização: qualquer membro dela,
	 * incluindo STAFF — cuidar dos animais é justamente o trabalho do staff.
	 */
	public boolean canManage(Pet pet, User actor) {
		return pet.isOwnedByOrganization()
				? organizations.isMember(pet.getOwnerOrg().getId(), actor)
				: pet.getOwnerUser().getId().equals(actor.getId());
	}

	public void requireCanManage(Pet pet, User actor) {
		if (!canManage(pet, actor)) {
			throw new AccessDeniedException("Você não administra este pet");
		}
	}
}
