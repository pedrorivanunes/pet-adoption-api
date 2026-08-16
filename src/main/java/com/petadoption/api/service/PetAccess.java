package com.petadoption.api.service;

import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Answers who may manage a pet -- edit it, remove it and decide applications
 * for it.
 *
 * <p>Same reason {@link OrganizationAccess} exists: the question is asked by
 * more than one service, and a duplicated authorization rule does not fail
 * loudly when the copies drift -- it just starts allowing what it should deny.
 */
@Component
public class PetAccess {

	private final OrganizationAccess organizations;

	public PetAccess(OrganizationAccess organizations) {
		this.organizations = organizations;
	}

	/**
	 * A person's pet: the owner only. An organization's pet: any member of it,
	 * STAFF included -- caring for the animals is exactly the staff's job.
	 */
	public boolean canManage(Pet pet, User actor) {
		return pet.isOwnedByOrganization()
				? organizations.isMember(pet.getOwnerOrg().getId(), actor)
				: pet.getOwnerUser().getId().equals(actor.getId());
	}

	public void requireCanManage(Pet pet, User actor) {
		if (!canManage(pet, actor)) {
			throw new AccessDeniedException("You do not manage this pet");
		}
	}
}
