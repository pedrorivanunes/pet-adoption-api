package com.petadoption.api.domain;

public enum ApplicationStatus {

	/** Aguardando decisão de quem cuida do pet. */
	PENDING,

	APPROVED,
	REJECTED,

	/** Desistência do próprio candidato. */
	CANCELED;

	/** Candidatura já decidida não muda mais de estado. */
	public boolean isFinal() {
		return this != PENDING;
	}
}
