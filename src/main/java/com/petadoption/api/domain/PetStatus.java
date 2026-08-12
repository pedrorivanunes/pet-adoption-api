package com.petadoption.api.domain;

public enum PetStatus {

	AVAILABLE,
	ADOPTED,
	LOST,
	DECEASED;

	/** Estado terminal: não há transição de volta a partir dele. */
	public boolean isTerminal() {
		return this == DECEASED;
	}
}
