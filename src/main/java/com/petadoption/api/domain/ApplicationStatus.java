package com.petadoption.api.domain;

public enum ApplicationStatus {

	/** Waiting on a decision from whoever manages the pet. */
	PENDING,

	APPROVED,
	REJECTED,

	/** Withdrawn by the applicant themselves. */
	CANCELED;

	/** Once decided, an application does not change state again. */
	public boolean isFinal() {
		return this != PENDING;
	}
}
