package com.petadoption.api.domain;

public enum PetStatus {

	AVAILABLE,
	ADOPTED,
	LOST,
	DECEASED;

	/** A terminal state: there is no transition back out of it. */
	public boolean isTerminal() {
		return this == DECEASED;
	}
}
